package com.parth.emergency_dashboard.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class SmsService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String fromNumber;

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (enabled) {
            Twilio.init(accountSid, authToken);
            System.out.println("✅ Twilio SMS service initialized");
        } else {
            System.out.println("⚠️  Twilio SMS disabled — set twilio.enabled=true in application.properties");
        }
    }

    // ── SEND SMS ON REPORT ────────────────────────────
    public void sendReportConfirmation(String toPhone, String type, String location, String trackingId, String priority) {
        if (!enabled) {
            System.out.printf("📱 [SMS MOCK] To: %s | Emergency reported: %s at %s | ID: %s%n",
                    toPhone, type, location, trackingId);
            return;
        }
        String msg = String.format(
            "🚨 EMERGEX: Your %s emergency at %s has been reported.\n" +
            "Priority: %s\n" +
            "Tracking ID: %s\n" +
            "Use this ID to track your case status.",
            type, location, priority, trackingId
        );
        sendSms(toPhone, msg);
    }

    // ── SEND SMS ON RESOLVE ───────────────────────────
    public void sendResolveNotification(String toPhone, String type, String location, String trackingId) {
        if (!enabled) {
            System.out.printf("📱 [SMS MOCK] To: %s | Emergency resolved: %s at %s | ID: %s%n",
                    toPhone, type, location, trackingId);
            return;
        }
        String msg = String.format(
            "✅ EMERGEX: Your %s emergency at %s has been RESOLVED.\n" +
            "Tracking ID: %s\n" +
            "Thank you for using Emergex.",
            type, location, trackingId
        );
        sendSms(toPhone, msg);
    }

    // ── INTERNAL SEND ─────────────────────────────────
    private void sendSms(String toPhone, String body) {
        try {
            // Ensure phone has country code — prepend +91 if it's a 10-digit Indian number
            String formattedPhone = toPhone.startsWith("+") ? toPhone : "+91" + toPhone;

            Message message = Message.creator(
                new PhoneNumber(formattedPhone),
                new PhoneNumber(fromNumber),
                body
            ).create();

            System.out.println("✅ SMS sent — SID: " + message.getSid());
        } catch (Exception e) {
            System.err.println("❌ SMS failed: " + e.getMessage());
        }
    }
}