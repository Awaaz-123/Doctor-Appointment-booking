package com.careconnect.service;

import com.careconnect.model.*;
import com.careconnect.repository.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    // INTELLIGENCE MAP: Symptoms -> Doctor
 // INTELLIGENCE MAP: Symptoms -> Doctor
    private static final Map<String, List<String>> KNOWLEDGE_BASE = new HashMap<>();

    static {
        // --- EXISTING CATEGORIES (Expanded) ---
        KNOWLEDGE_BASE.put("Cardiologist", Arrays.asList(
            "heart", "chest", "pain", "attack", "pulse", "pressure", "palpitation", "angina", "shortness of breath", "cardiac"
        ));
        
        KNOWLEDGE_BASE.put("Dermatologist", Arrays.asList(
            "skin", "rash", "acne", "hair", "itch", "spots", "pimples", "mole", "eczema", "scalp", "burn", "allergy"
        ));
        
        KNOWLEDGE_BASE.put("Ophthalmologist", Arrays.asList(
            "eye", "vision", "blur", "blind", "see", "glasses", "lens", "sight", "cataract", "redness", "watery"
        ));
        
        KNOWLEDGE_BASE.put("Dentist", Arrays.asList(
            "tooth", "teeth", "gum", "cavity", "mouth", "root canal", "brace", "filling", "ache", "floss", "dental"
        ));
        
        KNOWLEDGE_BASE.put("Neurologist", Arrays.asList(
            "brain", "headache", "dizzy", "migraine", "nerves", "seizure", "stroke", "tremor", "memory", "numbness", "tingling"
        ));
        
        KNOWLEDGE_BASE.put("General Physician", Arrays.asList(
            "fever", "cold", "flu", "checkup", "cough", "weakness", "vomit", "nausea", "fatigue", "viral", "infection", "sick"
        ));

        // --- NEW CATEGORIES ADDED ---
        KNOWLEDGE_BASE.put("Orthopedist", Arrays.asList(
            "bone", "joint", "knee", "fracture", "back", "spine", "muscle", "ligament", "shoulder", "arthritis", "sprain", "injury"
        ));

        KNOWLEDGE_BASE.put("Pediatrician", Arrays.asList(
            "child", "baby", "kid", "infant", "toddler", "growth", "vaccine", "pediatric", "newborn"
        ));

        KNOWLEDGE_BASE.put("ENT Specialist", Arrays.asList(
            "ear", "nose", "throat", "sinus", "hearing", "smell", "tonsil", "swallow", "voice", "sneeze"
        ));

        KNOWLEDGE_BASE.put("Gynecologist", Arrays.asList(
            "period", "pregnancy", "menstrual", "baby", "womb", "fertility", "cramps", "cycle", "reproductive"
        ));

        KNOWLEDGE_BASE.put("Psychiatrist", Arrays.asList(
            "depression", "anxiety", "stress", "mental", "sleep", "insomnia", "mood", "panic", "therapy", "sad"
        ));
    }
    private final Map<String, ConversationState> sessions = new ConcurrentHashMap<>();
    private final AppointmentRepository repo;

    public ConversationService(AppointmentRepository repo) {
        this.repo = repo;
    }

    public String handleMessage(String sessionId, String rawMsg) {
        if (rawMsg == null || rawMsg.trim().isEmpty()) return "Please type something.";
        String msg = rawMsg.trim();

        // 1. GLOBAL RESET COMMANDS
        if (msg.equalsIgnoreCase("reset") || msg.equalsIgnoreCase("cancel")) {
            sessions.remove(sessionId);
            return "🔄 Session reset. Say 'Hi' to start a new booking.";
        }

        // 2. NEW: GREETING HANDLER (Fixes your issue)
        // If user says "hello", force a restart even if we were waiting for a date
        if (msg.equalsIgnoreCase("hello") || msg.equalsIgnoreCase("hi") || msg.equalsIgnoreCase("hey")) {
            sessions.remove(sessionId); // Clear old memory
            ConversationState newState = new ConversationState();
            newState.setCurrentStep(ConversationState.Step.AWAITING_NAME); // Jump straight to Name
            sessions.put(sessionId, newState);
            return "Hello! I am your AI Care Assistant. What is your **Name**?";
        }

        ConversationState state = sessions.computeIfAbsent(sessionId, k -> new ConversationState());

        // ... rest of your switch/case logic ...
        try {
            switch (state.getCurrentStep()) {
                case START:
                    state.setCurrentStep(ConversationState.Step.AWAITING_NAME);
                    return "Hello! I am your AI Care Assistant. What is your **Name**?";

                case AWAITING_NAME:
                    if (!msg.matches("^[a-zA-Z\\s]+$")) return "❌ names cannot contain numbers. Please enter text only.";
                    state.setName(msg);
                    state.setCurrentStep(ConversationState.Step.AWAITING_SPECIALIZATION);
                    return "Nice to meet you, " + state.getName() + ". \n\nTell me your problem (e.g., 'I have eye pain', 'need a heart checkup') or name the specialist.";

                case AWAITING_SPECIALIZATION:
                    // INTELLIGENT DETECTION
                    String detectedDoc = detectSpecialization(msg);

                    if (detectedDoc == null) {
                        return "🤔 I couldn't identify the medical field based on \"" + msg + "\".\n" +
                               "Please try standard terms like: " + String.join(", ", KNOWLEDGE_BASE.keySet());
                    }

                    state.setSpecialization(detectedDoc);
                    state.setCurrentStep(ConversationState.Step.AWAITING_DATE);
                    return "I understand. I have assigned a **" + detectedDoc + "** for you.\n" +
                           "When would you like to come? (e.g., 'Tomorrow', 'Next Monday', or '2025-05-20')";

                case AWAITING_DATE:
                    // INTELLIGENT DATE PARSING
                    LocalDate date = smartDateParser(msg);
                    
                    if (date == null) {
                        return "❌ I didn't understand that date. Please use 'Tomorrow', 'Next Friday', or 'YYYY-MM-DD'.";
                    }
                    if (date.isBefore(LocalDate.now())) {
                        return "❌ You cannot book an appointment in the past (" + date + "). Try a future date.";
                    }

                    state.setDate(date.toString());
                    state.setCurrentStep(ConversationState.Step.AWAITING_TIME);
                    return "Okay, booked for **" + date.getDayOfWeek() + ", " + date + "**.\n" +
                           "What time? (e.g., '10:00' or '2 PM')";

                case AWAITING_TIME:
                    // INTELLIGENT TIME PARSING
                    String time = smartTimeParser(msg);
                    if(time == null) {
                         return "❌ Invalid time. Try '09:00', '14:30' or '5 PM'.";
                    }
                    
                    state.setTime(time);
                    saveAppointment(state);
                    sessions.remove(sessionId);
                    return "✅ **Appointment Confirmed!**\n\n" +
                           "👤 Patient: " + state.getName() + "\n" +
                           "👨‍⚕️ Doctor: " + state.getSpecialization() + "\n" +
                           "📅 Date: " + state.getDate() + "\n" +
                           "⏰ Time: " + state.getTime();

                default:
                    return "System Error. Type 'reset'.";
            }
        } catch (Exception e) {
            logger.error("Session Error", e);
            sessions.remove(sessionId);
            return "❌ Something went wrong. I have reset your session.";
        }
    }

    // --- 🧠 AI BRAIN: SYMPTOM MATCHER ---
 // --- 🧠 AI BRAIN: SYMPTOM MATCHER (Updated for Word Priority) ---
    private String detectSpecialization(String input) {
        String lowerInput = input.toLowerCase();

        // 1. Direct Doctor Name Match (Highest Priority)
        // e.g. "I want a Dentist"
        for (String doc : KNOWLEDGE_BASE.keySet()) {
            if (lowerInput.contains(doc.toLowerCase())) return doc;
        }

        // 2. Word-by-Word Scanning (User's Logic)
        // Split sentence into words: "leg pain" -> ["leg", "pain"]
        String[] words = lowerInput.split("\\s+"); // Split by whitespace

        for (String word : words) {
            // Check this specific word against ALL doctors
            for (Map.Entry<String, List<String>> entry : KNOWLEDGE_BASE.entrySet()) {
                for (String keyword : entry.getValue()) {
                    // Exact match or contains (e.g., "legs" contains "leg")
                    if (word.contains(keyword)) {
                        return entry.getKey(); // Return the FIRST match found in the sentence
                    }
                }
            }
        }

        return null; // No match found
    }

    // --- 🧠 AI BRAIN: DATE PARSER ---
    private LocalDate smartDateParser(String input) {
        String lowerInput = input.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        try {
            if (lowerInput.contains("today")) return today;
            if (lowerInput.contains("tomorrow")) return today.plusDays(1);
            if (lowerInput.contains("day after")) return today.plusDays(2);
            
            // Handle "Next Monday", "Next Friday" etc.
            if (lowerInput.startsWith("next ")) {
                String dayName = lowerInput.replace("next ", "").trim();
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (dayName.equalsIgnoreCase(day.name())) {
                        return today.with(TemporalAdjusters.next(day));
                    }
                }
            }

            // Fallback to strict YYYY-MM-DD
            return LocalDate.parse(input);
        } catch (Exception e) {
            return null;
        }
    }
    
    // --- 🧠 AI BRAIN: TIME PARSER ---
    private String smartTimeParser(String input) {
        input = input.toUpperCase().replace(" ", ""); // Remove spaces, make upper
        try {
            // Handle "5PM", "10AM"
            if (input.contains("PM")) {
                int hour = Integer.parseInt(input.replace("PM", ""));
                if (hour != 12) hour += 12;
                return LocalTime.of(hour, 0).toString();
            }
            if (input.contains("AM")) {
                int hour = Integer.parseInt(input.replace("AM", ""));
                if (hour == 12) hour = 0;
                return LocalTime.of(hour, 0).toString();
            }
            // Handle standard HH:mm
            return LocalTime.parse(input).toString();
        } catch (Exception e) {
             // Try standard parse if the custom logic fails
             try { return LocalTime.parse(input).toString(); } catch (Exception ex) { return null; }
        }
    }

    private void saveAppointment(ConversationState state) {
        Appointment a = new Appointment();
        a.setPatientName(state.getName());
        a.setSpecialization(state.getSpecialization());
        a.setDate(state.getDate());
        a.setTime(state.getTime());
        repo.save(a);
    }
}