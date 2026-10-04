package com.workoutrec.setup

import com.workoutrec.automation.AutomationStatus
import com.workoutrec.automation.AutomationStatusEvaluator
import com.workoutrec.automation.ScriptInstall
import com.workoutrec.data.SelectedAccount
import com.workoutrec.data.SettingsStore
import com.workoutrec.data.SpreadsheetBinding
import com.workoutrec.google.ApiError
import com.workoutrec.spreadsheet.SpreadsheetSetup
import com.workoutrec.spreadsheet.SpreadsheetSnapshot
import com.workoutrec.spreadsheet.StructureCheckResult
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * US2: find or create the spreadsheet, attach the script, guide the two automation steps
 * (data-model.md "Setup flow"). The spreadsheet is the source of truth; the binding is a cache.
 */
class SpreadsheetSetupFlow(
    private val setup: SpreadsheetSetup,
    private val installer: ScriptInstall,
    private val store: SettingsStore,
    private val timeZone: () -> String,
    private val now: () -> Instant,
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
            SetupAction.FixAutomation -> fixAutomation(account)
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
        return RefreshResult.Status(reminder(snapshot))
    }

    private suspend fun findSpreadsheet(account: SelectedAccount): SetupState = guarded(SetupStep.Finding) {
        val found = setup.find() ?: return@guarded createNew(account)
        checkExisting(account, found.id)
    }

    private suspend fun checkExisting(account: SelectedAccount, spreadsheetId: String): SetupState =
        guarded(SetupStep.Checking) {
            val check = setup.check(spreadsheetId)
            when (check.result) {
                is StructureCheckResult.Mismatch -> SetupState.AskRewrite(spreadsheetId)
                StructureCheckResult.Match -> {
                    bind(account, spreadsheetId, check.snapshot.metadata)
                    afterMatch(account, spreadsheetId, check.snapshot)
                }
            }
        }

    private suspend fun afterMatch(account: SelectedAccount, spreadsheetId: String, snapshot: SpreadsheetSnapshot): SetupState =
        when (AutomationStatusEvaluator.evaluate(snapshot.metadata, now())) {
            AutomationStatus.ScriptMissing -> attach(account, spreadsheetId, snapshot.metadata, snapshot.metadataIds)
            AutomationStatus.NotEnabled -> snapshot.metadata[AutomationStatusEvaluator.ENABLE_URL]
                ?.let { SetupState.NeedsEnable(it) }
                ?: attach(account, spreadsheetId, snapshot.metadata, snapshot.metadataIds)
            AutomationStatus.On, AutomationStatus.Stopped -> SetupState.Ready
        }

    private suspend fun createNew(account: SelectedAccount): SetupState = guarded(SetupStep.Creating) {
        val id = setup.create(timeZone())
        bind(account, id, emptyMap())
        addTablesThenAttach(account, id)
    }

    private suspend fun rewrite(account: SelectedAccount, oldId: String): SetupState = guarded(SetupStep.Renaming, oldId) {
        val id = setup.rewrite(oldId, timeZone())
        bind(account, id, emptyMap())
        addTablesThenAttach(account, id)
    }

    /**
     * Tables need their own request after create (spreadsheets.create ignores them). The
     * spreadsheet is already bound, so a failure here is a Creating error whose Retry repairs this
     * spreadsheet instead of creating another one.
     */
    private suspend fun addTablesThenAttach(account: SelectedAccount, spreadsheetId: String): SetupState =
        guarded(SetupStep.Creating) {
            setup.ensureTables(spreadsheetId)
            attach(account, spreadsheetId, emptyMap(), emptyMap())
        }

    private suspend fun retryCreating(account: SelectedAccount): SetupState {
        val binding = store.binding.first() ?: return createNew(account)
        return guarded(SetupStep.Creating) {
            setup.ensureTables(binding.spreadsheetId)
            attachToBound(account)
        }
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
            when (AutomationStatusEvaluator.evaluate(snapshot.metadata, now())) {
                AutomationStatus.On -> SetupState.Ready
                else -> current
            }
        }
    }

    private suspend fun fixAutomation(account: SelectedAccount): SetupState {
        val binding = store.binding.first() ?: return findSpreadsheet(account)
        return guarded(SetupStep.Checking) {
            val snapshot = setup.check(binding.spreadsheetId).snapshot
            when (AutomationStatusEvaluator.evaluate(snapshot.metadata, now())) {
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

    private fun reminder(snapshot: SpreadsheetSnapshot): Reminder? =
        when (val status = AutomationStatusEvaluator.evaluate(snapshot.metadata, now())) {
            AutomationStatus.On -> null
            else -> Reminder(status, snapshot.metadata[AutomationStatusEvaluator.ENABLE_URL])
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

    /** Maps Google failures to [SetupState.Error] for [step] (FR-011). */
    private suspend fun guarded(step: SetupStep, rewriteId: String? = null, block: suspend () -> SetupState): SetupState =
        try {
            block()
        } catch (e: ApiError) {
            if (step == SetupStep.Renaming) failedRewriteId = rewriteId
            SetupState.Error(step, e)
        }
}
