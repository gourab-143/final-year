package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securesamvad.R;
import com.example.securesamvad.crypto.CryptoHelper;          // ✅
import com.example.securesamvad.model.User;
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

public class VerifyOTPActivity extends AppCompatActivity {

    /* six OTP boxes */
    private EditText d1,d2,d3,d4,d5,d6;
    private Button   verifyBtn;
    private TextView resendLink, phoneText;
    private ProgressBar progress;

    private String verificationId, fullPhone;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otpactivity);

        /* bind views */
        d1 = findViewById(R.id.d1);  d2 = findViewById(R.id.d2);  d3 = findViewById(R.id.d3);
        d4 = findViewById(R.id.d4);  d5 = findViewById(R.id.d5);  d6 = findViewById(R.id.d6);

        verifyBtn  = findViewById(R.id.verify);
        resendLink = findViewById(R.id.resendCode);
        phoneText  = findViewById(R.id.textMobile);
        progress   = findViewById(R.id.progressbar);

        /* extras from RegisterActivity */
        verificationId = getIntent().getStringExtra("verificationId");
        fullPhone      = getIntent().getStringExtra("mobile");
        resendToken    = VerifyOTPActivityHolder.token;

        if (fullPhone != null) phoneText.setText(fullPhone);

        setUpOTPInputs();

        verifyBtn.setOnClickListener(v -> verifyCode());
        resendLink.setOnClickListener(v -> resendCode());
    }

    /* ---------------- auto‑move focus ---------------- */
    private void setUpOTPInputs() {
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int st,int bef,int cnt){
                if (s.length()==1) moveNext();
            }
            private void moveNext() {
                if      (d1.isFocused()) d2.requestFocus();
                else if (d2.isFocused()) d3.requestFocus();
                else if (d3.isFocused()) d4.requestFocus();
                else if (d4.isFocused()) d5.requestFocus();
                else if (d5.isFocused()) d6.requestFocus();
            }
        };
        d1.addTextChangedListener(tw); d2.addTextChangedListener(tw);
        d3.addTextChangedListener(tw); d4.addTextChangedListener(tw);
        d5.addTextChangedListener(tw); d6.addTextChangedListener(tw);
    }

    /* ---------------- verify OTP ---------------- */
    private void verifyCode() {
        String code = d1.getText().toString()+d2.getText().toString()+
                d3.getText().toString()+d4.getText().toString()+
                d5.getText().toString()+d6.getText().toString();

        if (code.length()!=6) {
            Toast.makeText(this,"Enter 6‑digit OTP",Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(ProgressBar.VISIBLE);

        PhoneAuthCredential cred = PhoneAuthProvider.getCredential(verificationId, code);
        FirebaseAuth.getInstance().signInWithCredential(cred)
                .addOnCompleteListener(task -> {
                    progress.setVisibility(ProgressBar.GONE);
                    if (task.isSuccessful()) {
                        saveUserToDatabase();                       // ✅ stores pubKey
                        startActivity(new Intent(this, MainActivity.class));
                        finishAffinity();
                    } else {
                        Toast.makeText(this,"Invalid OTP",Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /* ---------------- resend OTP ---------------- */
    private void resendCode() {
        if (resendToken == null || fullPhone == null) {
            Toast.makeText(this,"Please wait before resending",Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(ProgressBar.VISIBLE);

        PhoneAuthOptions opts = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(fullPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setForceResendingToken(resendToken)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override public void onVerificationCompleted(@NonNull PhoneAuthCredential c){}
                    @Override public void onVerificationFailed(@NonNull FirebaseException e){
                        progress.setVisibility(ProgressBar.GONE);
                        Toast.makeText(VerifyOTPActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onCodeSent(@NonNull String vid,
                                                     @NonNull PhoneAuthProvider.ForceResendingToken tok){
                        progress.setVisibility(ProgressBar.GONE);
                        verificationId = vid;
                        resendToken    = tok;
                        Toast.makeText(VerifyOTPActivity.this,"OTP resent",Toast.LENGTH_SHORT).show();
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(opts);
    }

    /* ---------------- save user (with pubKey) ---------------- */
    /* ---------------- save user (with pubKey) ---------------- */
    private void saveUserToDatabase() {
        FirebaseUser f = FirebaseAuth.getInstance().getCurrentUser();
        if (f == null) return;

        String uid   = f.getUid();
        String phone = f.getPhoneNumber();

        Map<String,Object> profile = new HashMap<>();
        profile.put("phone", phone);
        profile.put("name",  "New User");

        try {
            String myPub = CryptoHelper.getMyPublicKey(this);
            profile.put("pubKey", myPub);
        } catch (Exception ignored) { }

        DatabaseReference userRef =
                FirebaseDatabase.getInstance().getReference("users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    userRef.setValue(profile);       // first login
                } else {
                    userRef.updateChildren(profile); // merge, keep chats/
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }


    /* ---------------- static holder (resend) ---------------- */
    public static class VerifyOTPActivityHolder {
        public static PhoneAuthProvider.ForceResendingToken token;
    }
}
