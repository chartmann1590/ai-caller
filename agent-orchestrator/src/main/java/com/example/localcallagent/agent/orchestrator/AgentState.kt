package com.example.localcallagent.agent.orchestrator

enum class AgentState {
    PRE_CALL,
    DIALING,
    WAITING_FOR_ANSWER,
    DISCLOSURE,
    ASKING,
    LISTENING,
    THINKING,
    CLARIFYING,
    HANDOFF,
    GOODBYE,
    COMPLETE,
    FAILED
}
