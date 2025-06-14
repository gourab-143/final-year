package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.securesamvad.R;
import com.example.securesamvad.model.User;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.TimeUnit;

public class VerifyOTPActivity extends AppCompatActivity {

    /* six boxes */
    private EditText d1,d2,d3,d4,d5,d6;
    private Button   verifyBtn;
    private TextView resendLink, phoneText;
    private ProgressBar progress;

    private String verificationId, fullPhone;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_verify_otpactivity);

        /* bind XML */
        d1 = findViewById(R.id.d1);  d2 = findViewById(R.id.d2);  d3 = findViewById(R.id.d3);
        d4 = findViewById(R.id.d4);  d5 = findViewById(R.id.d5);  d6 = findViewById(R.id.d6);
        verifyBtn   = findViewById(R.id.verify);
        resendLink  = findViewById(R.id.resendCode);
        phoneText   = findViewById(R.id.textMobile);
        progress    = findViewById(R.id.progressbar);

        /* get extras */
        verificationId = getIntent().getStringExtra("verificationId");
        fullPhone      = getIntent().getStringExtra("mobile");          // "+91…"
        resendToken    = VerifyOTPActivityHolder.token;                 // see note below
        if (fullPhone != null) phoneText.setText(fullPhone);

        /* auto‑move cursor */
        setUpOTPInputs();

        verifyBtn.setOnClickListener(v -> verifyCode());
        resendLink.setOnClickListener(v -> resendCode());
    }

    /* ---------- helper: auto‑focus between boxes ---------- */
    private void setUpOTPInputs() {
        TextWatcher mover = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int st,int bef,int cnt){
                if (s.length()==1) moveNext();
            }
            private void moveNext(){
                if      (d1.isFocused()) d2.requestFocus();
                else if (d2.isFocused()) d3.requestFocus();
                else if (d3.isFocused()) d4.requestFocus();
                else if (d4.isFocused()) d5.requestFocus();
                else if (d5.isFocused()) d6.requestFocus();
            }
        };
        d1.addTextChangedListener(mover); d2.addTextChangedListener(mover);
        d3.addTextChangedListener(mover); d4.addTextChangedListener(mover);
        d5.addTextChangedListener(mover); d6.addTextChangedListener(mover);
    }

    /* ---------- verify ---------- */
    private void verifyCode() {
        String code = d1.getText().toString() + d2.getText().toString() +
                d3.getText().toString() + d4.getText().toString() +
                d5.getText().toString() + d6.getText().toString();

        if (code.length()!=6) {
            Toast.makeText(this,"Enter 6‑digit OTP",Toast.LENGTH_SHORT).show();
            return;
        }
        progress.setVisibility(ProgressBar.VISIBLE);

        PhoneAuthCredential cred = PhoneAuthProvider.getCredential(verificationId, code);
        FirebaseAuth.getInstance().signInWithCredential(cred)
                .addOnCompleteListener(t -> {
                    progress.setVisibility(ProgressBar.GONE);
                    if (t.isSuccessful()) {
                        saveUserToDatabase();
                        startActivity(new Intent(this, MainActivity.class));
                        finishAffinity();
                    } else {
                        Toast.makeText(this,"Invalid OTP",Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /* ---------- resend ---------- */
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

    /* ---------- save user in DB ---------- */
    private void saveUserToDatabase() {
        FirebaseUser f = FirebaseAuth.getInstance().getCurrentUser();
        if (f == null) return;
        User u = new User(f.getUid(), f.getPhoneNumber(),"New User");

        FirebaseDatabase.getInstance().getReference("users")
                .child(f.getUid()).setValue(u);
    }

    /* ---------- static holder for resending token ---------- */
    public static class VerifyOTPActivityHolder {
        public static PhoneAuthProvider.ForceResendingToken token;
    }
}
