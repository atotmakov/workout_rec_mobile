package com.workoutrec.fakes

import com.workoutrec.auth.AccountPicker
import com.workoutrec.auth.PickResult

class FakeAccountPicker(private val result: PickResult) : AccountPicker {
    var calls = 0

    override suspend fun pick(): PickResult {
        calls++
        return result
    }
}
