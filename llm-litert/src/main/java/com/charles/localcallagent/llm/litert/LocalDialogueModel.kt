package com.charles.localcallagent.llm.litert

import com.charles.localcallagent.core.model.AgentDecision
import com.charles.localcallagent.core.model.DialogueContext

/**
 * PHASE F / PHASE 12 — Local Dialogue Model Interface.
 * Operates offline. Generates structured AgentDecision objects.
 */
interface LocalDialogueModel {
    suspend fun decide(context: DialogueContext): AgentDecision
}
