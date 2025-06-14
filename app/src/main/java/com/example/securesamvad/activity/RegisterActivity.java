package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.firebase.database.FirebaseDatabase;

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
    private void saveUserToDatabase() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;

        User user = new User(u.getUid(), u.getPhoneNumber(), "New User");

        try {
            String myPub = CryptoHelper.getMyPublicKey(this); // ✅ generate / fetch
            user.setPubKey(myPub);                            // ✅ add to model
        } catch (Exception e) {
            Toast.makeText(this,"Key error: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }

        FirebaseDatabase.getInstance().getReference("users")
                .child(u.getUid())
                .setValue(user)
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Save failed: "+e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }
}
