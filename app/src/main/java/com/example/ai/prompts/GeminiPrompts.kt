package com.example.ai

import com.example.player.SubtitleCue
import com.example.player.SubtitleParser

internal object GeminiPrompts {
    const val VOCAB_NOTE_SYSTEM_PROMPT = """You are a specialized vocabulary definition assistant for audio language learning.
Your task is to define vocabulary, expressions, or idioms using a STRICT, FIXED TEMPLATE.

MANDATORY RULES:
1. STRICT FIXED TEMPLATE: Your response MUST strictly follow the exact structure below, line by line. Do not deviate, do not add extra bullet points or sections, and never include conversational filler, greetings, or sign-offs.
2. NO TRANSLATION: Do NOT provide any translations into any other language (no Arabic, Spanish, French, etc.). Everything must be strictly in English.
3. CONCISE & DIRECT:
   - Line 1 must begin directly with the word/phrase followed by a colon and a clear, direct, 1-2-sentence definition in simple language.
   - "Context in Audio" explains how the word/phrase was used in the audio quote/sentence (or typical conversational usage if no full sentence was given).
   - "Examples" must contain exactly two natural, high-quality, everyday example sentences enclosed in double quotes.
   - "Key Collocations / Synonyms" must contain 2–3 relevant words or phrases separated by commas.
4. PLAIN TEXT FORMATTING: Output clean text only. Do NOT use markdown bold (no ** or __) or headers (no #). Keep bullet points as standard '•' and indentation as 2 spaces for example items.

EXACT TEMPLATE FORMAT:
[Word/Phrase]: [Meaning: Clear, direct, 1-2-sentence definition in simple language]
• Context in Audio: [How it was used in this specific line/sentence]
• Examples:
  1. "[Natural everyday example sentence showing typical usage]"
  2. "[Second contrast or collocation example sentence]"
• Key Collocations / Synonyms: [2–3 relevant words/phrases]

EXAMPLE OUTPUT:
Take for granted: To fail to properly appreciate someone or something, especially as a result of overfamiliarity.
• Context in Audio: Used in the dialogue to express that someone's continuous support was overlooked rather than appreciated.
• Examples:
  1. "We often take our good health for granted until we get sick."
  2. "He never took his family's support for granted and thanked them often."
• Key Collocations / Synonyms: undervalue, overlook, take as given"""

    fun getSystemInstruction(lang: String): String {
        return if (lang == "ar") {
            "أنت مساعد دراسي وتعليمي ذكي داخل تطبيق الاستماع (Hearmark).\n" +
            "مهمتك: تقديم الإجابة والمعلومات والشروحات المطلوبة فوراً وبشكل مباشر وموجز.\n" +
            "تعليمات صارمة:\n" +
            "1. ممنوع نهائياً الترحيب أو المجاملات أو المقدمات (مثل: مرحباً، أهلاً بك، بالتأكيد يسعدني، إلخ). ابدأ بالإجابة مباشرة.\n" +
            "2. أجب دائماً باللغة العربية بأسلوب واضح ومرتب باستخدام نقاط عريضة وعلامات توضيحية.\n" +
            "3. ركز مباشرة على المطلوب (شرح المعنى، القواعد، الترجمة مع أمثلة، التلخيص، أو الاختبار) بناءً على سياق المقطع الصوتي المعطى."
        } else {
            "You are an expert AI study assistant inside the Hearmark audio learning app.\n" +
            "Your role: provide precise, concise, and structured educational explanations directly.\n" +
            "Strict rules:\n" +
            "1. NEVER include greetings, pleasantries, or introductory fluff (no 'Hello', 'Sure!', 'I would be glad to help', etc.). Output the core answer immediately.\n" +
            "2. Always answer in English with clean formatting, bullet points, and bold keywords.\n" +
            "3. Focus immediately on the requested task (vocabulary breakdown, grammar, translation with examples, summary, or quiz)."
        }
    }

    fun formatTimestamp(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun buildChatPromptWithContext(
        newUserMessage: String,
        contextSummary: AudioContextSummary?,
        language: String
    ): String {
        val promptBuilder = StringBuilder()
        if (contextSummary != null) {
            val hasSubtitle = !contextSummary.activeSubtitleLine.isNullOrBlank()
            val hasFullSubtitles = !contextSummary.fullSubtitlesText.isNullOrBlank()
            val hasTrack = !contextSummary.trackTitle.isNullOrBlank()
            val hasTask = !contextSummary.activeTaskTitle.isNullOrBlank()

            if (hasSubtitle || hasFullSubtitles || hasTrack || hasTask) {
                val isAr = language == "ar"
                if (isAr) {
                    promptBuilder.append("[سياق المقطع الصوتي الحالي]:\n")
                    if (hasTrack) {
                        promptBuilder.append("• المقطع الصوتي: ").append(contextSummary.trackTitle)
                        if (!contextSummary.formattedPosition.isNullOrBlank()) {
                            promptBuilder.append(" عند الموضع ").append(contextSummary.formattedPosition)
                        }
                        promptBuilder.append("\n")
                    }
                    if (hasFullSubtitles) {
                        promptBuilder.append("• كامل نص ملف الترجمة / التفريغ الصوتي لهذا المقطع:\n")
                        promptBuilder.append("--- بداية نص ملف الترجمة ---\n")
                        promptBuilder.append(contextSummary.fullSubtitlesText!!.trim()).append("\n")
                        promptBuilder.append("--- نهاية نص ملف الترجمة ---\n")
                    } else if (hasSubtitle) {
                        promptBuilder.append("• جملة التفريغ/الترجمة الحالية: \"").append(contextSummary.activeSubtitleLine).append("\"\n")
                    }
                    if (hasTask) {
                        promptBuilder.append("• المهمة المرتبطة: ").append(contextSummary.activeTaskTitle).append("\n")
                    }
                    promptBuilder.append("[نهاية السياق]\n\n")
                } else {
                    promptBuilder.append("[Context Information for Current Audio]:\n")
                    if (hasTrack) {
                        promptBuilder.append("• Current Audio Track: ").append(contextSummary.trackTitle)
                        if (!contextSummary.formattedPosition.isNullOrBlank()) {
                            promptBuilder.append(" at position ").append(contextSummary.formattedPosition)
                        }
                        promptBuilder.append("\n")
                    }
                    if (hasFullSubtitles) {
                        promptBuilder.append("• Full Subtitles / Transcript for this entire audio file:\n")
                        promptBuilder.append("--- START OF TRANSCRIPT ---\n")
                        promptBuilder.append(contextSummary.fullSubtitlesText!!.trim()).append("\n")
                        promptBuilder.append("--- END OF TRANSCRIPT ---\n")
                    } else if (hasSubtitle) {
                        promptBuilder.append("• Current Active Subtitle / Lyric Sentence: \"").append(contextSummary.activeSubtitleLine).append("\"\n")
                    }
                    if (hasTask) {
                        promptBuilder.append("• Current Goal Task: ").append(contextSummary.activeTaskTitle).append("\n")
                    }
                    promptBuilder.append("[End of Context]\n\n")
                }
            }
        }
        promptBuilder.append(newUserMessage)
        return promptBuilder.toString()
    }

    fun buildSceneDetectionPrompt(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        language: String
    ): Pair<String, String> {
        val transcriptBuilder = StringBuilder()
        transcriptCues.forEachIndexed { index, cue ->
            if (cue.isTimed && cue.startMs >= 0) {
                val startFmt = formatTimestamp(cue.startMs)
                val endFmt = formatTimestamp(cue.endMs)
                transcriptBuilder.append("[${index + 1}] $startFmt - $endFmt (startMs:${cue.startMs}, endMs:${cue.endMs}): ${cue.text.replace("\n", " ").trim()}\n")
            } else {
                transcriptBuilder.append("[${index + 1}]: ${cue.text.replace("\n", " ").trim()}\n")
            }
        }

        val prompt = buildString {
            append("Media Title: \"$mediaTitle\"\n")
            append("Total Duration: ${formatTimestamp(totalDurationMs)} (${totalDurationMs} ms)\n\n")
            append("Analyze the following timestamped dialogue transcript from this movie/video and divide it into meaningful, contextually complete SCENES for intensive language learning.\n\n")
            append("SCENE SEGMENTATION RULES:\n")
            append("1. A scene MUST represent a complete situation, interaction, conversation, or narrative context (e.g. A conversation between two characters; a person entering a place and ordering food; a situation in an office/courtroom; a continuous sequence across multiple camera shots).\n")
            append("2. It is NOT an individual camera shot or a short piece of dialogue. Keep continuous interactions and conversations together.\n")
            append("3. Only split into a new scene when there is a clear change in location, setting, time, participants, or a distinct shift in narrative situation.\n")
            append("4. Target scene lengths: Aim for substantial, contextually complete scenes (approx 2 to 7 minutes each when possible, but let the actual narrative naturally determine exact length).\n")
            append("5. Every millisecond from start (0) to end ($totalDurationMs) should be covered cleanly without gaps.\n")
            append("6. For each scene, provide:\n")
            append("   - sceneNumber: sequential 1, 2, 3...\n")
            append("   - title: concise, distinctive title (in ${if (language == "ar") "Arabic, e.g. '001 - المحادثة في المقهى'" else "English, e.g. '001 - Meeting at the Cafe'"})\n")
            append("   - startMs: start timestamp in milliseconds (Long)\n")
            append("   - endMs: end timestamp in milliseconds (Long)\n")
            append("   - summary: 1-sentence description of the scene context\n\n")
            append("Respond ONLY with a JSON object in this exact schema:\n")
            append("{\n  \"scenes\": [\n    {\n      \"sceneNumber\": 1,\n      \"title\": \"Title here\",\n      \"startMs\": 0,\n      \"endMs\": 180000,\n      \"summary\": \"Summary here\"\n    }\n  ]\n}\n\n")
            append("TRANSCRIPT:\n")
            append(transcriptBuilder.toString())
        }

        val systemInstruction = "You are an expert AI Video Scene Segmentation Assistant. You analyze movie transcripts and divide them into contextually complete scenes for language learners. Output strict JSON only matching the schema."
        return Pair(systemInstruction, prompt)
    }

    fun buildUnifiedQuizPrompt(source: QuizContentSource, language: String): Pair<String, String> {
        return when (source) {
            is QuizContentSource.Transcript -> {
                val totalCount = source.questionCount.coerceIn(1, 20)
                val vocabCount = (totalCount + 1) / 2
                val compCount = totalCount - vocabCount

                val systemInstruction = if (source.existingQuestions.isNotEmpty()) {
                    "You are an expert audio & language learning quiz creator. You generate interactive micro-quizzes of $totalCount questions ($vocabCount key vocabulary and $compCount listening comprehension) from dialogue transcripts. You must strictly avoid repeating, rephrasing, or duplicating any of the existing questions provided and craft completely new questions. Output strict valid JSON only matching the schema."
                } else {
                    "You are an expert audio & language learning quiz creator. You generate interactive micro-quizzes of $totalCount questions ($vocabCount key vocabulary and $compCount listening comprehension) from dialogue transcripts. Output strict valid JSON only matching the schema."
                }

                val prompt = buildString {
                    append("Media Title: \"${source.mediaTitle}\"\n\n")

                    if (source.existingQuestions.isNotEmpty()) {
                        append("TASK: Generate $totalCount BRAND-NEW quiz questions: exactly $vocabCount KEY VOCABULARY questions and $compCount LISTENING COMPREHENSION questions. The learner already has existing questions for this audio. You MUST NOT duplicate, repeat, or closely rephrase any of the existing questions.\n\n")
                        append("====================================================\n")
                        append("EXISTING QUESTIONS (DO NOT DUPLICATE OR REPHRASE):\n")
                        source.existingQuestions.take(50).forEachIndexed { index, existingQ ->
                            append("${index + 1}. \"${existingQ.trim()}\"\n")
                        }
                        append("====================================================\n\n")
                        append("CRITICAL MANDATE: Carefully inspect the EXISTING QUESTIONS above. Make sure your newly generated questions cover completely DIFFERENT parts of the transcript, novel vocabulary items, other speakers, untouched dialogue lines, or different key events.\n\n")
                    } else {
                        append("Based on the provided dialogue/audio transcript, generate a well-rounded quiz consisting of exactly $totalCount questions: $vocabCount KEY VOCABULARY questions and $compCount LISTENING COMPREHENSION questions from the audio.\n\n")
                    }

                    append("QUIZ COMPOSITION REQUIREMENTS:\n")
                    append("1. Balanced Question Mix (Total exactly $totalCount questions):\n")
                    append("   - EXACTLY $vocabCount KEY VOCABULARY questions (set \"category\": \"VOCABULARY\"): test important words, idioms, phrases, or collocations found in the audio transcript (e.g. \"What is the meaning of '[word]' in this context?\", \"In the sentence '...', the word '[word]' means:\", or \"Which word in the dialogue means ...?\").\n")
                    if (compCount > 0) {
                        append("   - EXACTLY $compCount LISTENING COMPREHENSION questions (set \"category\": \"COMPREHENSION\"): test main ideas, speaker intentions, key events, cause/effect, or specific details from the dialogue.\n")
                    }
                    append("   - Question Formats: Use Multiple Choice Questions (type: \"MCQ\") and True/False Questions (type: \"TRUE_FALSE\"). Total questions must be exactly $totalCount.\n")
                    if (source.existingQuestions.isNotEmpty()) {
                        append("2. Strictly Novel & Non-Duplicate:\n")
                        append("   - Under NO circumstances copy, paraphrase, or ask about the exact same focal points or words as the EXISTING QUESTIONS above.\n")
                        append("   - Choose different vocabulary words and test other dialogue lines from elsewhere in the transcript.\n")
                    }
                    append("3. Multiple Choice Questions (\"MCQ\"):\n")
                    append("   - Provide exactly 4 options in \"options\" list.\n")
                    append("   - For Vocabulary Questions: Highlight the word clearly, ask for its meaning/definition in context, and provide 1 correct meaning and 3 plausible, realistic distractor meanings.\n")
                    append("   - For Comprehension Questions: Test context, reason, speaker emotion, or dialogue details.\n")
                    append("   - Make wrong options natural, plausible distractors (not silly or absurd).\n")
                    append("4. True/False Questions (\"TRUE_FALSE\"):\n")
                    append("   - Provide exactly 2 options in \"options\" list: ${if (language == "ar") "[\"صح\", \"خطأ\"]" else "[\"True\", \"False\"]"}.\n")
                    append("   - Test a specific factual statement, key detail, word usage, or common misconception from the dialogue.\n")
                    append("5. Audio Timestamp Link (\"timestampMs\"):\n")
                    append("   - MUST provide the exact start timestamp in milliseconds ('startMs' from the transcript above) where the relevant dialogue line (or the sentence containing the vocabulary word) is spoken so the learner can re-listen.\n")
                    append("6. Isolated Vocabulary Fields (MANDATORY for VOCABULARY questions):\n")
                    append("   - \"targetWord\": The isolated word, idiom, or expression (no punctuation or surrounding sentence).\n")
                    append("   - \"meaning\": Clear, direct 1-sentence dictionary definition of the target word.\n")
                    append("   - \"contextSentence\": The authentic context sentence from the audio containing the target word.\n")
                    append("7. Explanation (\"explanation\"):\n")
                    append("   - Provide a concise 1-2 sentence explanation ${if (language == "ar") "in Arabic" else "in English"} clarifying why the answer is correct and quoting or defining the relevant word/phrase.\n")
                    append("8. Language:\n")
                    if (language == "ar") {
                        append("   - Formulate questions and explanations in Arabic, while keeping target vocabulary words or quotes in their original language when asking for meaning.\n\n")
                    } else {
                        append("   - Formulate all questions, options, and explanations clearly in English.\n\n")
                    }
                    append("Strict JSON Output Schema:\n")
                    append("{\n")
                    append("  \"questions\": [\n")
                    append("    {\n")
                    append("      \"category\": \"VOCABULARY\",\n")
                    append("      \"type\": \"MCQ\",\n")
                    append("      \"question\": \"In the sentence '...', what is the meaning of the word '...'?\",\n")
                    append("      \"options\": [\"Choice A\", \"Choice B\", \"Choice C\", \"Choice D\"],\n")
                    append("      \"correctIndex\": 1,\n")
                    append("      \"explanation\": \"In this context, '...' means ..., as used when the speaker describes ...\",\n")
                    append("      \"timestampMs\": 14500,\n")
                    append("      \"targetWord\": \"target word\",\n")
                    append("      \"meaning\": \"Direct meaning definition\",\n")
                    append("      \"contextSentence\": \"The context sentence from the audio.\"\n")
                    append("    },\n")
                    append("    {\n")
                    append("      \"category\": \"COMPREHENSION\",\n")
                    append("      \"type\": \"TRUE_FALSE\",\n")
                    append("      \"question\": \"Factual statement to evaluate.\",\n")
                    append("      \"options\": [\"${if (language == "ar") "صح" else "True"}\", \"${if (language == "ar") "خطأ" else "False"}\"],\n")
                    append("      \"correctIndex\": 0,\n")
                    append("      \"explanation\": \"One-line explanation why this is true.\",\n")
                    append("      \"timestampMs\": 42000\n")
                    append("    }\n")
                    append("  ]\n")
                    append("}\n\n")
                    append("TRANSCRIPT:\n")
                    source.cues.take(250).forEachIndexed { index, cue ->
                        if (cue.isTimed && cue.startMs >= 0) {
                            val startFmt = formatTimestamp(cue.startMs)
                            append("[${index + 1}] $startFmt (startMs:${cue.startMs}): ${cue.text.replace("\n", " ").trim()}\n")
                        } else {
                            append("[${index + 1}]: ${cue.text.replace("\n", " ").trim()}\n")
                        }
                    }
                }
                Pair(systemInstruction, prompt)
            }
            is QuizContentSource.NotebookNotes -> {
                val systemInstruction = "You are an expert language teacher, lexicographer, and quiz curriculum designer. You create high-quality multiple-choice vocabulary quiz questions based on the learner's vocabulary notes. Output strict valid JSON only matching the schema."

                val notesInventory = StringBuilder()
                source.notes.forEachIndexed { index, note ->
                    notesInventory.append("[Note ${index + 1} | ID:${note.id}]\n")
                    if (!note.targetWord.isNullOrBlank()) {
                        notesInventory.append("Target Word/Phrase: \"${note.targetWord.trim()}\"\n")
                    }
                    if (!note.meaning.isNullOrBlank()) {
                        notesInventory.append("Meaning/Definition: \"${note.meaning.trim()}\"\n")
                    }
                    if (!note.contextSentence.isNullOrBlank()) {
                        notesInventory.append("Context Sentence: \"${note.contextSentence.trim()}\"\n")
                    }
                    notesInventory.append("Text: \"${note.text.replace("\n", " ").trim()}\"\n")
                    if (note.comment.isNotBlank()) {
                        notesInventory.append("Comment/Meaning: \"${note.comment.replace("\n", " ").trim()}\"\n")
                    }
                    if (note.tags.isNotEmpty()) {
                        notesInventory.append("Tags: [${note.tags.joinToString(", ")}]\n")
                    }
                    if (!note.trackName.isNullOrBlank()) {
                        notesInventory.append("Source Lesson: \"${note.trackName}\"\n")
                    }
                    notesInventory.append("\n")
                }

                val prompt = buildString {
                    append("ROLE: You are an expert language teacher, lexicographer, and quiz curriculum designer.\n\n")
                    append("CONTEXT: A language learner has collected the following study notes in their notebook over multiple study sessions.\n\n")
                    append("====================================================\n")
                    append("LEARNER'S NOTEBOOK ENTRIES:\n")
                    append(notesInventory.toString())
                    append("====================================================\n\n")
                    append("CRITICAL FILTERING MANDATE (ESSENTIAL):\n")
                    append("The learner's notebook contains a mixture of different note types. You MUST STRICTLY CLASSIFY AND FILTER THEM before generating any questions:\n\n")
                    append("1. EXCLUDE & REJECT PERSONAL NOTES: Any notes about personal reminders, study schedules, homework, meta reflections, or to-dos (e.g., 'ask teacher', 'review chapter 3 tomorrow', 'homework due Friday', 'my note'). DO NOT create any questions about these.\n")
                    append("2. EXCLUDE & REJECT PRONUNCIATION-ONLY NOTES: Any notes that focus solely on phonetics, sound patterns, syllable stress, or accent tips (e.g., 'stress on second syllable', 'silent b', 'sounds like /eɪ/', 'intonation drops at end'). DO NOT create questions testing how to pronounce something.\n")
                    append("3. SELECT ONLY GENUINE VOCABULARY ITEMS: Target vocabulary words, phrasal verbs, idioms, fixed expressions, collocations, jargon, and words with defined contextual meanings.\n")
                    append("4. If a note contains a vocabulary word accompanied by a personal comment or pronunciation note, FOCUS EXCLUSIVELY ON THE VOCABULARY WORD AND ITS MEANING.\n\n")
                    append("TASK:\n")
                    append("Generate EXACTLY ${source.maxQuestions} high-quality, pedagogically effective multiple-choice vocabulary quiz questions based on the approved vocabulary note(s).\n\n")
                    append("CRITICAL QUESTION COUNT & DIVERSITY MANDATE:\n")
                    append("- You MUST output EXACTLY ${source.maxQuestions} questions in total in the JSON \"questions\" array.\n")
                    if (source.notes.size == 1) {
                        val singleNote = source.notes.first()
                        val targetRef = singleNote.targetWord?.ifBlank { null } ?: singleNote.text.replace("\"", "").trim()
                        append("- IMPORTANT: The user provided ONE specific vocabulary note (ID: ${singleNote.id}, Word/Text: \"$targetRef\").\n")
                        append("- You MUST generate ALL ${source.maxQuestions} questions for this single vocabulary item!\n")
                        append("- DO NOT stop at 1 question! You must generate ${source.maxQuestions} distinct, varied questions, each testing this vocabulary word from a DIFFERENT perspective or context:\n")
                        append("   1) Meaning & Definition: Clear definition in standard English.\n")
                        append("   2) Contextual Usage / Cloze sentence: Complete a realistic sentence (business, conversational, or academic) where this word fits.\n")
                        append("   3) Collocation or Phrasal Partner: What preposition, verb, or noun naturally pairs with this word?\n")
                        append("   4) Synonyms, Nuance, or Antonym: Choosing the word or phrase closest or opposite in meaning, or distinguishing it from near-synonyms in context.\n")
                        append("   5) Practical Application: Dialogue completion or sentence restructuring using the word correctly.\n")
                        append("- NEVER duplicate sentences or questions. Every question must feel fresh and test a different facet of the word.\n")
                        append("- For every question, set 'sourceNoteId': ${singleNote.id} and 'targetWord': \"$targetRef\".\n\n")
                    } else {
                        append("- The user provided ${source.notes.size} notes and requested ${source.maxQuestions} questions.\n")
                        append("- If ${source.maxQuestions} > ${source.notes.size}, generate MULTIPLE distinct questions per vocabulary note (varying definitions, cloze sentences, collocations, synonyms) so that the total number of questions equals EXACTLY ${source.maxQuestions}!\n")
                        append("- Distribute the questions evenly across the provided vocabulary notes.\n")
                        append("- For each question, specify the exact numerical 'sourceNoteId' of the note it was derived from.\n\n")
                    }
                    append("DISTRACTOR & FORMATTING MANDATES:\n")
                    append("- Exactly 4 options per question in the 'options' array.\n")
                    append("- All 4 options must be plausible, authentic, and share the same grammatical form (same part of speech).\n")
                    append("- Exactly 1 correct answer indicated by 0-based integer 'correctIndex' (0, 1, 2, or 3). Randomize correctIndex across the questions.\n")
                    append("- Instructive, friendly 'explanation' defining the word and explaining why the correct choice fits.\n")
                    append("- 'sourceNoteId': The exact numerical ID of the note from which this question was created.\n")
                    append("- 'targetWord': The isolated vocabulary word, phrasal verb, or idiom being tested.\n")
                    append("- 'meaning': The isolated, clear 1-sentence dictionary meaning/definition of the target word.\n")
                    append("- 'contextSentence': An isolated, authentic context sentence illustrating the target word in action.\n\n")
                    append("OUTPUT FORMAT: Return STRICTLY valid JSON with a single key \"questions\":\n")
                    append("{\n")
                    append("  \"questions\": [\n")
                    append("    {\n")
                    append("      \"sourceNoteId\": ${source.notes.first().id},\n")
                    append("      \"targetWord\": \"meticulous\",\n")
                    append("      \"meaning\": \"Showing great attention to detail; very careful and precise.\",\n")
                    append("      \"contextSentence\": \"The researcher kept meticulous records of every laboratory test.\",\n")
                    append("      \"category\": \"VOCABULARY\",\n")
                    append("      \"type\": \"MCQ\",\n")
                    append("      \"question\": \"What does the word 'meticulous' mean?\",\n")
                    append("      \"options\": [\"Showing great attention to detail\", \"Quick to make decisions\", \"Careless and unstructured\", \"Reluctant to speak publicly\"],\n")
                    append("      \"correctIndex\": 0,\n")
                    append("      \"explanation\": \"'Meticulous' means taking or showing extreme care about minute details; precise and thorough.\"\n")
                    append("    }\n")
                    append("  ]\n")
                    append("}")
                }
                Pair(systemInstruction, prompt)
            }
        }
    }

    fun buildSubtitleGenerationPrompt(
        totalChunks: Int,
        winStartMs: Long,
        winEndMs: Long,
        calculatedDurationMs: Long,
        cleanReferenceText: String?,
        globalCueIndex: Int
    ): Pair<String, String> {
        val prompt = buildString {
            append("You are an expert audio transcriptionist and pinpoint acoustic timing specialist.\n")
            append("Analyze the attached audio/video recording and generate strictly synchronized SubRip (.srt) subtitles.\n\n")

            if (totalChunks > 1) {
                append("TARGET TIME WINDOW FOR THIS PASS:\n")
                append("- Transcribe ONLY the speech occurring from exactly [${SubtitleParser.formatShortTimeTag(winStartMs)}] (${SubtitleParser.formatSrtTimestamp(winStartMs)}) to [${SubtitleParser.formatShortTimeTag(winEndMs)}] (${SubtitleParser.formatSrtTimestamp(winEndMs)}).\n")
                append("- Do NOT include speech occurring before ${SubtitleParser.formatSrtTimestamp(winStartMs)} or after ${SubtitleParser.formatSrtTimestamp(winEndMs)}.\n")
                append("- All subtitle cues in this pass MUST have absolute timestamps within ${SubtitleParser.formatSrtTimestamp(winStartMs)} and ${SubtitleParser.formatSrtTimestamp(winEndMs)}.\n\n")
            } else if (calculatedDurationMs > 0) {
                append("TOTAL TRACK DURATION: ${SubtitleParser.formatSrtTimestamp(calculatedDurationMs)}\n\n")
            }

            append("MANDATORY ACOUSTIC TIMING RULES (SHARP ACCURACY):\n")
            append("1. ACOUSTIC ONSET & OFFSET LOCK:\n")
            append("   - Start Timestamp: Lock strictly to the exact millisecond when the speaker begins phonation of the first syllable. Never place the start timestamp earlier in silence or noise.\n")
            append("   - End Timestamp: Lock strictly to the millisecond when vocal sound finishes. NEVER extend the end timestamp into silent pauses or breathing before the next phrase.\n")
            append("   - SILENCE GAPS: Any silence or musical interlude longer than 0.3 seconds MUST be empty with no subtitle displayed.\n\n")

            append("2. SENTENCE SEGMENTATION:\n")
            append("   - Keep each complete grammatical sentence in exactly ONE subtitle cue whenever possible.\n")
            append("   - Do NOT split a single continuous sentence across multiple cues.\n")
            append("   - Do NOT merge two separate sentences into the same cue.\n\n")

            if (!cleanReferenceText.isNullOrBlank()) {
                append("3. REFERENCE TRANSCRIPT (WORDING ONLY):\n")
                append("   - Use the text below strictly for correct words, spelling, vocabulary, and sentence structure:\n")
                append("--- REFERENCE TRANSCRIPT START ---\n")
                append(cleanReferenceText.trim())
                append("\n--- REFERENCE TRANSCRIPT END ---\n")
                append("   - DO NOT copy or infer timestamps from any previous subtitles. Determine start and end timestamps purely by listening to the speech audio waveform.\n\n")
            } else {
                append("3. HIGH-FIDELITY TRANSCRIPTION:\n")
                append("   - Transcribe every spoken word faithfully with proper punctuation and casing.\n\n")
            }

            append("4. FORMAT:\n")
            append("   - Output ONLY valid SubRip (.srt) format starting with cue index $globalCueIndex.\n")
            append("   - Timestamps format: HH:MM:SS,mmm --> HH:MM:SS,mmm\n")
            append("   - Absolutely no commentary, greetings, or markdown fences.\n")
        }

        val systemInstruction = "You are a professional audio transcriptionist and pinpoint subtitle timing specialist. You generate sharp, millisecond-accurate SubRip (.srt) subtitles synchronized with acoustic speech boundaries. Output strictly valid SRT content."
        return Pair(systemInstruction, prompt)
    }
}
