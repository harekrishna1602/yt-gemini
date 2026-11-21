package com.studiomogli.ytgemini

object PromptFrameworks {

    private fun buildProfileContext(p: CreatorProfile): String {
        val format = if (p.isFaceless) "Faceless / Mask-framed (NO facecam)" else "Facecam on-camera"
        val niche = p.channelNiche.ifBlank { "General YouTube Creator" }
        val hw = p.hardwareSpecs.ifBlank { "Standard laptop/phone setup" }
        val tools = p.toolsAndApps.ifBlank { "Basic free editing tools" }
        return buildString {
            append("CREATOR CONSTRAINTS (STRICTLY RESPECT THESE):\n")
            append("- Niche: ").append(niche).append("\n")
            append("- Format: ").append(format).append("\n")
            append("- Hardware: ").append(hw).append("\n")
            append("- Tools/Apps owned: ").append(tools).append("\n")
            append("- Daily creation budget: ").append(p.dailyGoalMinutes).append(" mins/day\n")
            append("RULE: Never suggest high-budget shoots, facecam if faceless, or software they do not own.")
        }
    }

    fun voiceScriptToHook(profile: CreatorProfile, project: VideoProject): String {
        val ctx = buildProfileContext(profile)
        return buildString {
            append("You are an elite YouTube retention script editor. Do NOT rewrite my personality or sound robotic.\n")
            append(ctx).append("\n\n")
            append("VIDEO CORE ANGLE: ").append(project.coreAngle).append("\n")
            append("TARGET AUDIENCE: ").append(project.targetAudience.ifBlank { "My core subscribers" }).append("\n\n")
            append("MY RAW VOICE TRANSCRIPT:\n\"")
            append(project.voiceScript.ifBlank { project.coreAngle }).append("\"\n\n")
            append("TASK:\n")
            append("1. CLICK-TO-CONFIRM 15-SECOND HOOK (word-for-word, keeping my natural tone):\n")
            append("   - 0-3s: Immediate visual/verbal confirmation of the click (zero fluff).\n")
            append("   - 4-10s: Stakes / Tension (why this matters right now).\n")
            append("   - 11-15s: Open Loop (what they will discover if they stay).\n")
            append("2. CLEANED SCRIPT OUTLINE: Cut filler words from my raw voice dump and organize into Hook -> Setup -> Payoff beats I can record today.")
        }
    }

    fun noRepeatPackaging(profile: CreatorProfile, project: VideoProject): String {
        val ctx = buildProfileContext(profile)
        return buildString {
            append("You are a YouTube CTR & Thumbnail packaging strategist.\n")
            append(ctx).append("\n\n")
            append("VIDEO IDEA: ").append(project.coreAngle).append("\n")
            append("CURRENT DRAFT TITLE: ").append(project.finalTitle.ifBlank { "(none yet)" }).append("\n\n")
            append("TASK: Deliver 5 high-CTR Title + Thumbnail pairs using the NO-REPEAT PACKAGING MATRIX:\n")
            append("- Title must be under 55 characters (no mobile cutoff) and carry curiosity/context.\n")
            append("- Thumbnail text must be 2 to 4 words MAX and NEVER repeat words already in the Title.\n")
            append("- Describe the exact visual layout (respecting my Facecam/Faceless constraint above) so I can generate or shoot it today.")
        }
    }

    fun bingeToBuildFlip(profile: CreatorProfile, watchMins: Int, createMins: Int, bingeTopic: String): String {
        val ctx = buildProfileContext(profile)
        return buildString {
            append("You are my strict, no-excuses YouTube accountability coach.\n")
            append(ctx).append("\n\n")
            append("REALITY CHECK STATS TODAY:\n")
            append("- Time spent watching others: ").append(watchMins).append(" minutes\n")
            append("- Time spent creating in Workbench: ").append(createMins).append(" minutes\n")
            append("- What I kept watching: \"").append(bingeTopic).append("\"\n\n")
            append("TASK:\n")
            append("1. Give me a 2-sentence blunt reality check to snap me out of consumption mode.\n")
            append("2. BINGE-TO-BUILD FLIP: Take the exact topic I was watching and pitch 3 contrarian or remix video angles I can film TODAY using my exact setup above.\n")
            append("3. Give me the exact 1-sentence Core Angle to paste straight into my Workbench right now.")
        }
    }

    fun constraintFirstIdeas(profile: CreatorProfile): String {
        val ctx = buildProfileContext(profile)
        return buildString {
            append("You are a YouTube channel growth strategist. Do NOT ask me clarifying questions.\n")
            append(ctx).append("\n\n")
            append("TASK: Proactively pitch 3 high-upside, low-friction video ideas I have not thought of yet that fit my exact constraints above and convert viewers into subscribers.\n")
            append("For each idea give:\n")
            append("- 1-sentence Core Angle\n")
            append("- Clickable Title (<55 chars)\n")
            append("- 3-word Thumbnail Text (no repeated words from title)\n")
            append("- Why viewers will subscribe after watching.")
        }
    }
}
