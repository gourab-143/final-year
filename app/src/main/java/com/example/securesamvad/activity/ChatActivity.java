package com.example.securesamvad.activity;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.adapter.MessageAdapter;
import com.example.securesamvad.crypto.CryptoAES;
import com.example.securesamvad.crypto.CryptoHelper;
import com.example.securesamvad.model.Message;
import com.example.securesamvad.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One‑to‑one chat screen with AES‑GCM encryption.
 */
public class ChatActivity extends AppCompatActivity {

    /* ---------------- UI ---------------- */
    private RecyclerView messageRecycler;
    private EditText     messageInput;
    private ImageView    sendBtn;

    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter      adapter;

    /* ---------------- intent extras ---------------- */
    private String senderId;           // my uid
    private String receiverId;         // peer uid
    private String receiverName;
    private String receiverPhone;

    /* ---------------- Firebase ---------------- */
    private DatabaseReference rootUsers;
    private DatabaseReference myMsgRef;
    private DatabaseReference peerMsgRef;

    /* ---------------- E2EE ---------------- */
    private String myPubKey;           // cached locally
    private String peerPubKey;         // loaded from DB

    /* ====================================================================== */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        /* --- extras from previous screen --------------------------------- */
        senderId      = FirebaseAuth.getInstance().getUid();
        receiverId    = getIntent().getStringExtra("receiverId");
        receiverName  = getIntent().getStringExtra("receiverName");
        receiverPhone = getIntent().getStringExtra("receiverPhone");

        if (receiverId == null) { finish(); return; }
        if (receiverName  == null) receiverName  = receiverId;
        if (receiverPhone == null) receiverPhone = "";

        /* --- header text -------------------------------------------------- */
        ((TextView)findViewById(R.id.userName)).setText(receiverName);
        ((TextView)findViewById(R.id.number  )).setText(receiverPhone);

        /* --- view binding ------------------------------------------------- */
        messageRecycler = findViewById(R.id.messageRecyclerView);
        messageInput    = findViewById(R.id.messageInput);
        sendBtn         = findViewById(R.id.sendBtn);

        adapter = new MessageAdapter(new ArrayList<>());
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);                      // newest at bottom
        messageRecycler.setLayoutManager(lm);
        messageRecycler.setAdapter(adapter);

        /* --- Firebase refs ----------------------------------------------- */
        rootUsers  = FirebaseDatabase.getInstance().getReference("users");
        myMsgRef   = rootUsers.child(senderId) .child("chats").child(receiverId).child("messages");
        peerMsgRef = rootUsers.child(receiverId).child("chats").child(senderId ).child("messages");

        /* --- generate / fetch my public key ------------------------------ */
        try {
            myPubKey = CryptoHelper.getMyPublicKey(this);
        } catch (Exception e) {
            Toast.makeText(this,"Key error: "+e.getMessage(),Toast.LENGTH_LONG).show();
            finish(); return;
        }

        /* --- load peer’s public key then start listener ------------------ */
        rootUsers.child(receiverId).child("pubKey")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        peerPubKey = snap.getValue(String.class);
                        if (peerPubKey == null) {
                            Toast.makeText(ChatActivity.this,
                                    "Receiver hasn’t generated a key yet.",
                                    Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            attachMessageListener();   // keys ready ✔
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { }
                });

        /* --- send button -------------------------------------------------- */
        sendBtn.setOnClickListener(v -> sendEncrypted());

        /* --- create / update chat‑meta so it appears on Recent list ------- */
        long now = System.currentTimeMillis();
        String myPhone = FirebaseAuth.getInstance()
                .getCurrentUser().getPhoneNumber();

        rootUsers.child(senderId ).child("chats").child(receiverId).child("meta")
                .setValue(new User(receiverId, receiverPhone, receiverName, now));
        rootUsers.child(receiverId).child("chats").child(senderId ).child("meta")
                .setValue(new User(senderId,  myPhone,      "You",        now));
    }

    /* ====================================================================== */
    /* 1️⃣  Live listener – decrypt every message */
    private void attachMessageListener() {
        myMsgRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                messages.clear();

                for (DataSnapshot ds : snap.getChildren()) {
                    String cipher64 = ds.child("cipher").getValue(String.class);
                    String iv64     = ds.child("iv")    .getValue(String.class);
                    String uid      = ds.child("senderId").getValue(String.class);
                    Long   ts       = ds.child("timestamp").getValue(Long.class);
                    if (cipher64==null || iv64==null || uid==null || ts==null) continue;

                    try {
                        byte[] shared = CryptoHelper.deriveSharedKey(peerPubKey);
                        String plain    = CryptoAES.decrypt(cipher64, iv64, shared);
                        messages.add(new Message(ds.getKey(), uid, plain, ts));

                    } catch (GeneralSecurityException ignore) {
                        // corrupted / wrong‑key message – skip
                    } catch (Exception e) {
                        Log.e("ChatDecrypt", "err", e);
                    }
                }
                messages.sort((a,b)-> Long.compare(a.getTimestamp(), b.getTimestamp()));
                adapter.setMessages(messages);
                messageRecycler.scrollToPosition(adapter.getItemCount()-1);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }

    /* 2️⃣  Send encrypted -------------------------------------------------- */
    private void sendEncrypted() {
        String plain = messageInput.getText().toString().trim();
        if (plain.isEmpty()) return;
        if (peerPubKey == null) {
            Toast.makeText(this,"Key still loading…",Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            byte[] shared = CryptoHelper.deriveSharedKey(peerPubKey);
            CryptoAES.Bundle bundle = CryptoAES.encrypt(plain, shared);

            String id = UUID.randomUUID().toString();
            long ts   = System.currentTimeMillis();

            Map<String,Object> map = new HashMap<>();
            map.put("cipher",    bundle.cipher64);
            map.put("iv",        bundle.iv64);
            map.put("senderId",  senderId);
            map.put("timestamp", ts);

            myMsgRef  .child(id).setValue(map);
            peerMsgRef.child(id).setValue(map);

            messageInput.setText("");
        } catch (Exception e) {
            Toast.makeText(this,"Encrypt error: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
}
