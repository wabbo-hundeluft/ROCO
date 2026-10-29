package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test

class AiConfigurationInputTest {
    @Test fun pastedBoundaryWhitespaceIsNormalizedForBothProviders() {
        val c=AiConfiguration(geminiKey="  gemini-key\n",geminiModel=" gemini-flash-latest ",routerKey="\trouter-key ",routerModel=" provider/model:free ",shareHealthSummary=true).validated()
        assertEquals("gemini-key",c.geminiKey);assertEquals("router-key",c.routerKey)
        assertEquals("gemini-flash-latest",c.geminiModel);assertEquals("provider/model:free",c.routerModel)
        assertTrue(c.shareHealthSummary)
    }
    @Test fun embeddedWhitespaceIsRejectedWithoutLeakingCredentials() {
        val bad="sensitive key"
        val error=assertThrows(IllegalArgumentException::class.java) {AiConfiguration(routerKey=bad).validated()}
        assertFalse(error.message.orEmpty().contains(bad))
    }
    @Test fun headerControlCharactersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {AiConfiguration(routerKey="one\r\nTwo:header").validated()}
    }
    @Test fun modelValidationUsesProviderSpecificIds() {
        assertThrows(IllegalArgumentException::class.java) {AiConfiguration(geminiKey="key",geminiModel="https://wrong/model").validated()}
        assertEquals("provider/model:free",AiConfiguration(routerKey="key",routerModel="provider/model:free").validated().routerModel)
    }
    @Test fun removingKeysRemainsPossibleEvenWhenUnusedModelDraftIsInvalid() {
        val c=AiConfiguration(geminiModel="",routerModel="").validated()
        assertTrue(c.geminiKey.isEmpty());assertTrue(c.routerKey.isEmpty())
    }
}
