package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.securesamvad.R;
import com.example.securesamvad.model.User;
import com.example.securesamvad.crypto.CryptoHelper;            // ✅ NEW
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RegisterActivity extends AppCompatActivity {

    private EditText phoneInput;      // id = Mobile
    private Button   nextBtn;         // id = Next
    private FirebaseAuth auth;

    private String fullPhone;         // keep for intent

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content),
                (v,i)->{
                    Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(s.left,s.top,s.right,s.bottom);
                    return i;
                });

        phoneInput = findViewById(R.id.Mobile);
        nextBtn    = findViewById(R.id.Next);
        auth       = FirebaseAuth.getInstance();

        nextBtn.setOnClickListener(v -> startPhoneVerification());
    }

    /* ---------- phone‑number verification ---------- */
    private void startPhoneVerification() {
        String raw = phoneInput.getText().toString().trim();
        if (raw.isEmpty() || raw.length() < 10) {
            Toast.makeText(this,"Enter a valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        fullPhone = raw.startsWith("+") ? raw : "+91" + raw;

        PhoneAuthOptions opts = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(fullPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build();

        PhoneAuthProvider.verifyPhoneNumber(opts);
    }

    /* ---------- callbacks ---------- */
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override public void onVerificationCompleted(@NonNull PhoneAuthCredential cred) {
                    signInWithCredential(cred);        // auto OTP
                }

                @Override public void onVerificationFailed(@NonNull FirebaseException e) {
                    Toast.makeText(RegisterActivity.this,
                            "Verification failed: "+e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override public void onCodeSent(@NonNull String vid,
                                                 @NonNull PhoneAuthProvider.ForceResendingToken token) {

                    VerifyOTPActivity.VerifyOTPActivityHolder.token = token;   // keep for resend
                    Intent i = new Intent(RegisterActivity.this, VerifyOTPActivity.class);
                    i.putExtra("verificationId", vid);
                    i.putExtra("mobile", fullPhone);
                    startActivity(i);
                }
            };

    /* ---------- sign‑in ---------- */
    private void signInWithCredential(PhoneAuthCredential cred) {
        auth.signInWithCredential(cred).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                saveUserToDatabase();                       // ✅ now stores pubKey
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this,"Login failed",Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ---------- save user (uid, phone, name, pubKey) ---------- */
    /* ---------- save user (uid, phone, name, pubKey) ---------- */
    private void saveUserToDatabase() {
        FirebaseUser f = FirebaseAuth.getInstance().getCurrentUser();
        if (f == null) return;

        String uid   = f.getUid();
        String phone = f.getPhoneNumber();

        // fields we do (or may) want to update every sign‑in
        Map<String,Object> profile = new HashMap<>();
        profile.put("phone", phone);             // keep fresh
        profile.put("name",  "New User");        // only used first time

        profile.put("uid",   uid);

        try {
            String myPub = CryptoHelper.getMyPublicKey(this);
            profile.put("pubKey", myPub);
        } catch (Exception e) {
            Log.e("Crypto", "Cannot generate key", e);         // NEW  (or Toast)
            return;                                            // <‑‑ stop → let user retry
        }

        DatabaseReference userRef =
                FirebaseDatabase.getInstance().getReference("users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {

                /* first‑time sign‑in → create full node */
                if (!snap.exists()) {
                    userRef.setValue(profile);        // chats/ doesn’t exist yet
                    return;
                }

                /* returning user → merge profile fields, keep chats/ */
                userRef.updateChildren(profile);      // DOES NOT delete children
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }

}
