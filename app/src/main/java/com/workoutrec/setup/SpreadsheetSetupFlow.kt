package com.workoutrec.setup

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.automation.ScriptInstall
import com.workoutrec.automation.ScriptInstaller
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.google.ApiError
import com.workoutrec.monitoring.Monitoring
import com.workoutrec.monitoring.NoMonitoring
import com.workoutrec.monitoring.NonFatalIssue
import com.workoutrec.monitoring.badValue
import com.workoutrec.monitoring.failure
import com.workoutrec.spreadsheet.SpreadsheetSetup
import com.workoutrec.spreadsheet.SpreadsheetSnapshot
import com.workoutrec.spreadsheet.StructureCheckResult
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * US2: find or create the spreadsheet (data-model.md "Setup flow"). Feature 007: setup ends at Ready;
 * attaching the script and the two automation steps run only on [SetupAction.StartAutomation].
 * The spreadsheet is the source of truth; the binding is a cache.
 */
class SpreadsheetSetupFlow(
    private val setup: SpreadsheetSetup,
    private val installer: ScriptInstall,
    private val store: SettingsStore,
    private val timeZone: () -> String,
    private val now: () -> Instant,
    /** Diagnostic log, shared from the account menu. */
    private val log: (String) -> Unit = {},
    /** Feature 003: a mismatch as a non-fatal event (FR-005). */
    private val monitoring: Monitoring = NoMonitoring,
) : PostAuthStep {

    /** On launch with a binding: Ready at once, no network (FR-004); [refresh] checks in the background. */
    override suspend fun run(account: SelectedAccount): SetupState {
        if (store.binding.first() != null) return SetupState.Ready
        return findSpreadsheet(account)
    }

    override suspend fun onAction(account: SelectedAccount, current: SetupState, action: SetupAction): SetupState =
        when (action) {
            is SetupAction.AnswerRewrite -> {
                val oldId = (current as? SetupState.AskRewrite)?.spreadsheetId ?: return current
                if (action.yes) rewrite(account, oldId) else SetupState.SignedOut(message = null)
            }
            SetupAction.Retry -> when (current) {
                is SetupState.Error -> retry(account, current.step)
                else -> current
            }
            SetupAction.Recheck -> when (current) {
                SetupState.NeedsApiSetting -> attachToBound(account)
                is SetupState.NeedsEnable -> recheckEnabled(account, current)
                else -> current
            }
            SetupAction.ContinueForNow -> SetupState.Ready
            SetupAction.StartAutomation -> startAutomation(account)
        }

    /** data-model.md Ready: stored spreadsheet still there (call 11)? then automation status. */
    override suspend fun refresh(account: SelectedAccount): RefreshResult {
        val binding = store.binding.first() ?: return RefreshResult.NoChange
        if (!setup.exists(binding.spreadsheetId)) {
            store.clearBinding()
            return RefreshResult.SpreadsheetGone
        }
        val snapshot = setup.check(binding.spreadsheetId).snapshot
        bind(account, binding.spreadsheetId, snapshot.metadata)
        upgradeScript(binding.spreadsheetId, snapshot)
        return RefreshResult.Status(AutomationStatusEvaluator.evaluate(snapshot.metadata, now(), ::badSheetValue))
    }

    /**
     * Feature 006 research R7: an attached script older than the app's is replaced in the
     * background. A failure changes nothing the user sees; the next app start tries again.
     */
    private suspend fun upgradeScript(spreadsheetId: String, snapshot: SpreadsheetSnapshot) {
        val scriptId = snapshot.metadata[AutomationStatusEvaluator.SCRIPT_ID]?.takeIf { it.isNotBlank() } ?: return
        val version = snapshot.metadata[AutomationStatusEvaluator.SCRIPT_VERSION]?.toIntOrNull() ?: 1
        if (version >= ScriptInstaller.SCRIPT_VERSION) return
        try {
            installer.upgrade(spreadsheetId, scriptId, timeZone(), snapshot.metadataIds)
            log("setup: script upgraded to version ${ScriptInstaller.SCRIPT_VERSION}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("setup: script upgrade failed: ${e::class.simpleName}")
            monitoring.failure(NonFatalIssue.SCRIPT_UPGRADE_FAILED, e, "script-upgrade")
        }
    }

    private suspend fun findSpreadsheet(account: SelectedAccount): SetupState = guarded(SetupStep.Finding) {
        val found = setup.find() ?: return@guarded createNew(account)
        checkExisting(account, found.id)
    }

    private suspend fun checkExisting(account: SelectedAccount, spreadsheetId: String): SetupState =
        guarded(SetupStep.Checking) {
            val check = setup.check(spreadsheetId)
            when (val result = check.result) {
                is StructureCheckResult.Mismatch -> {
                    val reasons = result.reasons.joinToString("; ")
                    log("setup: spreadsheet $spreadsheetId does not match: $reasons")
                    monitoring.nonFatal(NonFatalIssue.SPREADSHEET_MISMATCH, SpreadsheetMismatch(reasons))
                    SetupState.AskRewrite(spreadsheetId)
                }
                StructureCheckResult.Match -> {
                    log("setup: spreadsheet $spreadsheetId matches")
                    bind(account, spreadsheetId, check.snapshot.metadata)
                    SetupState.Ready
                }
            }
        }

    private suspend fun createNew(account: SelectedAccount): SetupState = guarded(SetupStep.Creating) {
        val id = setup.create(timeZone())
        bind(account, id, emptyMap())
        addTables(id)
    }

    private suspend fun rewrite(account: SelectedAccount, oldId: String): SetupState = guarded(SetupStep.Renaming, oldId) {
        val id = setup.rewrite(oldId, timeZone())
        bind(account, id, emptyMap())
        addTables(id)
    }

    /**
     * Tables need their own request after create (spreadsheets.create ignores them). The
     * spreadsheet is already bound, so a failure here is a Creating error whose Retry repairs this
     * spreadsheet instead of creating another one.
     */
    private suspend fun addTables(spreadsheetId: String): SetupState =
        guarded(SetupStep.Creating) {
            setup.ensureTables(spreadsheetId)
            SetupState.Ready
        }

    private suspend fun retryCreating(account: SelectedAccount): SetupState {
        val binding = store.binding.first() ?: return createNew(account)
        return addTables(binding.spreadsheetId)
    }

    private suspend fun attach(
        account: SelectedAccount,
        spreadsheetId: String,
        metadata: Map<String, String>,
        metadataIds: Map<String, Int>,
    ): SetupState = guarded(SetupStep.AttachingScript) {
        try {
            val installed = installer.install(spreadsheetId, timeZone(), metadata, metadataIds)
            store.saveBinding(SpreadsheetBinding(account.email, spreadsheetId, installed.scriptId, installed.enableUrl))
            SetupState.NeedsEnable(installed.enableUrl)
        } catch (e: ApiError.AppsScriptApiDisabled) {
            SetupState.NeedsApiSetting
        }
    }

    /** Re-read the bound spreadsheet's metadata, then attach (step 1 done, or reminder tapped). */
    private suspend fun attachToBound(account: SelectedAccount): SetupState {
        val binding = store.binding.first() ?: return findSpreadsheet(account)
        return guarded(SetupStep.AttachingScript) {
            val snapshot = setup.check(binding.spreadsheetId).snapshot
            attach(account, binding.spreadsheetId, snapshot.metadata, snapshot.metadataIds)
        }
    }

    private suspend fun recheckEnabled(account: SelectedAccount, current: SetupState.NeedsEnable): SetupState {
        val binding = store.binding.first() ?: return current
        return guarded(SetupStep.Checking) {
            val snapshot = setup.check(binding.spreadsheetId).snapshot
            bind(account, binding.spreadsheetId, snapshot.metadata)
            when (AutomationStatusEvaluator.evaluate(snapshot.metadata, now(), ::badSheetValue)) {
                AutomationStatus.On -> SetupState.Ready
                else -> current
            }
        }
    }

    private suspend fun startAutomation(account: SelectedAccount): SetupState {
        val binding = store.binding.first() ?: return findSpreadsheet(account)
        return guarded(SetupStep.Checking) {
            val snapshot = setup.check(binding.spreadsheetId).snapshot
            when (AutomationStatusEvaluator.evaluate(snapshot.metadata, now(), ::badSheetValue)) {
                AutomationStatus.ScriptMissing -> attach(account, binding.spreadsheetId, snapshot.metadata, snapshot.metadataIds)
                AutomationStatus.On -> SetupState.Ready
                else -> snapshot.metadata[AutomationStatusEvaluator.ENABLE_URL]?.let { SetupState.NeedsEnable(it) }
                    ?: attach(account, binding.spreadsheetId, snapshot.metadata, snapshot.metadataIds)
            }
        }
    }

    /** The old spreadsheet ID of a failed rewrite, so Retry can repeat it. */
    private var failedRewriteId: String? = null

    private suspend fun retry(account: SelectedAccount, step: SetupStep): SetupState = when (step) {
        SetupStep.Finding, SetupStep.Checking -> findSpreadsheet(account)
        SetupStep.Creating -> retryCreating(account)
        SetupStep.Renaming -> failedRewriteId?.let { rewrite(account, it) } ?: findSpreadsheet(account)
        SetupStep.AttachingScript -> attachToBound(account)
    }

    private suspend fun bind(account: SelectedAccount, spreadsheetId: String, metadata: Map<String, String>) {
        store.saveBinding(
            SpreadsheetBinding(
                accountEmail = account.email,
                spreadsheetId = spreadsheetId,
                scriptId = metadata[AutomationStatusEvaluator.SCRIPT_ID],
                enableUrl = metadata[AutomationStatusEvaluator.ENABLE_URL],
            ),
        )
    }

    /** Feature 005 US3: an unreadable automation timestamp in the metadata. */
    private fun badSheetValue(value: String) = monitoring.badValue(NonFatalIssue.BAD_SHEET_DATA, value)

    /** Maps Google failures to [SetupState.Error] for [step] (FR-011). */
    private suspend fun guarded(step: SetupStep, rewriteId: String? = null, block: suspend () -> SetupState): SetupState =
        try {
            block()
        } catch (e: ApiError) {
            if (step == SetupStep.Renaming) failedRewriteId = rewriteId
            monitoring.failure(NonFatalIssue.SETUP_STEP_FAILED, e, "setup", "step=${step.name}")
            SetupState.Error(step, e)
        }
}

/** Reported when an existing spreadsheet fails the structure check (feature 003 FR-005). */
class SpreadsheetMismatch(reasons: String) : Exception("Spreadsheet does not match: $reasons")
