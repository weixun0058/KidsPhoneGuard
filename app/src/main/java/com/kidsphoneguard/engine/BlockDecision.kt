package com.kidsphoneguard.engine

data class BlockDecision(
    val shouldBlock: Boolean,
    val reason: BlockReason,
    val appName: String
)
