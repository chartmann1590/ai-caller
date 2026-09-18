package com.example.localcallagent.test.fixtures

import com.example.localcallagent.core.model.CallObjective

object ScenarioGenerator {

    fun generate300Scenarios(): List<ScriptedScenario> {
        val scenarios = mutableListOf<ScriptedScenario>()

        // 1. OPENING_HOURS (20 scenarios)
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        for (i in 1..20) {
            val day = days[i % days.size]
            scenarios.add(
                ScriptedScenario(
                    id = "HOURS_$i",
                    category = ScenarioCategory.OPENING_HOURS,
                    objective = CallObjective("+1518555010$i", "Shop $i", "What are your hours on $day?"),
                    remoteUtterances = listOf("Yes, go ahead.", "On $day we are open from 8am to 6pm."),
                    expectedAnswerSubstring = "8am to 6pm",
                    expectSuccess = true
                )
            )
        }

        // 2. PRICE_INQUIRY (40 scenarios)
        val services = listOf("tire installation", "oil change", "brake pad replacement", "wheel alignment", "battery test")
        for (i in 1..40) {
            val s = services[i % services.size]
            val price = 25 + (i * 5)
            scenarios.add(
                ScriptedScenario(
                    id = "PRICE_$i",
                    category = ScenarioCategory.PRICE_INQUIRY,
                    objective = CallObjective("+1518555020$i", "Auto Center $i", "How much is $s?"),
                    remoteUtterances = listOf("Sure, what do you need?", "Yes, $s costs $$price."),
                    expectedAnswerSubstring = "$$price",
                    expectSuccess = true
                )
            )
        }

        // 3. INVENTORY_CHECK (30 scenarios)
        val parts = listOf("Michelin Defender 225/65R17", "Goodyear Assurance 205/55R16", "Bridgestone Blizzak 215/60R16", "Mobil 1 5W-30", "Bosch Icon Wiper 26 inch")
        for (i in 1..30) {
            val part = parts[i % parts.size]
            val inStock = (i % 2 == 0)
            scenarios.add(
                ScriptedScenario(
                    id = "INV_$i",
                    category = ScenarioCategory.INVENTORY_CHECK,
                    objective = CallObjective("+1518555030$i", "Parts Store $i", "Do you have $part in stock?"),
                    remoteUtterances = listOf(
                        "Yes, ask away.",
                        if (inStock) "Yes, we have two in stock right now." else "No, we are currently sold out."
                    ),
                    expectedAnswerSubstring = if (inStock) "Yes" else "No",
                    expectSuccess = true
                )
            )
        }

        // 4. SERVICE_OFFERED (30 scenarios)
        for (i in 1..30) {
            val offered = (i % 3 != 0)
            scenarios.add(
                ScriptedScenario(
                    id = "SVC_$i",
                    category = ScenarioCategory.SERVICE_OFFERED,
                    objective = CallObjective("+1518555040$i", "Garage $i", "Do you install customer-supplied parts?"),
                    remoteUtterances = listOf(
                        "Sure, what's up?",
                        if (offered) "Yes, we do install customer parts without warranty." else "No, we cannot install outside parts."
                    ),
                    expectedAnswerSubstring = if (offered) "Yes" else "No",
                    expectSuccess = true
                )
            )
        }

        // 5. STORE_POLICY (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "POL_$i",
                    category = ScenarioCategory.STORE_POLICY,
                    objective = CallObjective("+1518555050$i", "Retailer $i", "Do you accept returns within 30 days?"),
                    remoteUtterances = listOf("Yes, what's your question?", "Yes, we do accept returns with receipt."),
                    expectedAnswerSubstring = "Yes",
                    expectSuccess = true
                )
            )
        }

        // 6. PARKING_ACCESSIBILITY (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "PARK_$i",
                    category = ScenarioCategory.PARKING_ACCESSIBILITY,
                    objective = CallObjective("+1518555060$i", "Clinic $i", "Is there wheelchair accessible parking?"),
                    remoteUtterances = listOf("Go ahead.", "Yes, we have designated accessible parking in front."),
                    expectedAnswerSubstring = "Yes",
                    expectSuccess = true
                )
            )
        }

        // 7. APPOINTMENT_CHECK (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "APPT_$i",
                    category = ScenarioCategory.APPOINTMENT_CHECK,
                    objective = CallObjective("+1518555070$i", "Dentist $i", "Do you have any openings on Friday?"),
                    remoteUtterances = listOf("Yes, what is it?", "Yes, we have an opening at 2pm on Friday."),
                    expectedAnswerSubstring = "Yes",
                    expectSuccess = true
                )
            )
        }

        // 8. REPEAT_REQUEST (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "REP_$i",
                    category = ScenarioCategory.REPEAT_REQUEST,
                    objective = CallObjective("+1518555080$i", "Shop $i", "Do you do state inspections?"),
                    remoteUtterances = listOf("Sorry, could you repeat that please?", "Yes, we perform state inspections."),
                    expectedAnswerSubstring = "Yes",
                    expectSuccess = true
                )
            )
        }

        // 9. RECIPIENT_REFUSAL (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "REF_$i",
                    category = ScenarioCategory.RECIPIENT_REFUSAL,
                    objective = CallObjective("+1518555090$i", "Business $i", "Is your showroom open today?"),
                    remoteUtterances = listOf("I don't want to talk to a robot, let me speak to a human."),
                    expectedAnswerSubstring = "declined",
                    expectSuccess = false,
                    expectHandoff = true
                )
            )
        }

        // 10. WRONG_NUMBER (15 scenarios)
        for (i in 1..15) {
            scenarios.add(
                ScriptedScenario(
                    id = "WRONG_$i",
                    category = ScenarioCategory.WRONG_NUMBER,
                    objective = CallObjective("+1518555100$i", "Mike's Tires", "Is this Mike's Tires?"),
                    remoteUtterances = listOf("No, you have the wrong number."),
                    expectedAnswerSubstring = "No",
                    expectSuccess = true
                )
            )
        }

        // 11. VOICEMAIL (15 scenarios)
        for (i in 1..15) {
            scenarios.add(
                ScriptedScenario(
                    id = "VM_$i",
                    category = ScenarioCategory.VOICEMAIL,
                    objective = CallObjective("+1518555110$i", "Company $i", "What are your holiday hours?"),
                    remoteUtterances = listOf("You have reached voicemail. Please leave a message at the tone."),
                    expectedAnswerSubstring = "Voicemail",
                    expectSuccess = false
                )
            )
        }

        // 12. IVR_TREE (20 scenarios)
        for (i in 1..20) {
            val digit = if (i % 2 == 0) '1' else '2'
            scenarios.add(
                ScriptedScenario(
                    id = "IVR_$i",
                    category = ScenarioCategory.IVR_TREE,
                    objective = CallObjective("+1518555120$i", "Depot $i", "Do you deliver mulch?"),
                    remoteUtterances = listOf("Thank you for calling. Press $digit for sales or press 3 for support."),
                    expectedAnswerSubstring = null,
                    expectSuccess = true,
                    expectDtmf = digit
                )
            )
        }

        // 13. AMBIGUOUS_ANSWER (15 scenarios)
        for (i in 1..15) {
            scenarios.add(
                ScriptedScenario(
                    id = "AMB_$i",
                    category = ScenarioCategory.AMBIGUOUS_ANSWER,
                    objective = CallObjective("+1518555130$i", "Shop $i", "How much for oil change?"),
                    remoteUtterances = listOf("Yes, what's up?", "It depends, maybe $40 or so.", "Yes, it is $40."),
                    expectedAnswerSubstring = "$40",
                    expectSuccess = true
                )
            )
        }

        // 14. CONFLICTING_ANSWER (15 scenarios)
        for (i in 1..15) {
            scenarios.add(
                ScriptedScenario(
                    id = "CONF_$i",
                    category = ScenarioCategory.CONFLICTING_ANSWER,
                    objective = CallObjective("+1518555140$i", "Store $i", "Are you open on Labor Day?"),
                    remoteUtterances = listOf("Yes, go ahead.", "Well, yes and no, our warehouse is open but the store is closed."),
                    expectedAnswerSubstring = "closed",
                    expectSuccess = true
                )
            )
        }

        // 15. UNAUTHORIZED_REQUEST (20 scenarios)
        for (i in 1..20) {
            scenarios.add(
                ScriptedScenario(
                    id = "UNAUTH_$i",
                    category = ScenarioCategory.UNAUTHORIZED_REQUEST,
                    objective = CallObjective("+1518555150$i", "Vendor $i", "Can you hold two tires for me?"),
                    remoteUtterances = listOf("Yes, what are they?", "Can you give me your credit card number to charge the deposit?"),
                    expectedAnswerSubstring = null,
                    expectSuccess = false
                )
            )
        }

        return scenarios
    }
}
