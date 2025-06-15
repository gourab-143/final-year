package com.example.securesamvad.activity;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.adapter.MessageAdapter;
import com.example.securesamvad.crypto.CryptoAES;
import com.example.securesamvad.crypto.CryptoHelper;
import com.example.securesamvad.model.Message;
import com.example.securesamvad.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.security.GeneralSecurityException;
import java.util.*;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messageRV;
    private EditText     messageInput;
    private ImageView    sendBtn;

    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter adapter;

    private String senderId, receiverId;
    private String receiverName, receiverPhone;

    private DatabaseReference myMsgRef, yourMsgRef, rootUsers;

    /* E2EE */
    private String myPubKey;        // Base64 of *this* device
    private String receiverPubKey;  // Base64 of the chat partner

    /* ------------------------------------------------------------------ */
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v,i)->{
            Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left,s.top,s.right,s.bottom); return i; });

        /* -------- extras -------- */
        senderId      = FirebaseAuth.getInstance().getUid();
        receiverId    = getIntent().getStringExtra("receiverId");
        receiverName  = getIntent().getStringExtra("receiverName");
        receiverPhone = getIntent().getStringExtra("receiverPhone");
        if (receiverId == null) { finish(); return; }
        if (receiverName == null)  receiverName  = receiverId;
        if (receiverPhone== null)  receiverPhone = "";

        ((TextView)findViewById(R.id.userName)).setText(receiverName);
        ((TextView)findViewById(R.id.number  )).setText(receiverPhone);

        /* -------- Firebase paths -------- */
        rootUsers  = FirebaseDatabase.getInstance().getReference("users");
        myMsgRef   = rootUsers.child(senderId) .child("chats").child(receiverId).child("messages");
        yourMsgRef = rootUsers.child(receiverId).child("chats").child(senderId ).child("messages");

        /* -------- my public key -------- */
        try { myPubKey = CryptoHelper.getMyPublicKey(this); }
        catch (Exception e) {
            Toast.makeText(this,"Key error: "+e,Toast.LENGTH_LONG).show();
            finish(); return;
        }

        /* -------- load receiver pubKey THEN attach listener -------- */
        rootUsers.child(receiverId).child("pubKey")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        receiverPubKey = snap.getValue(String.class);
                        if (receiverPubKey == null) {
                            Toast.makeText(ChatActivity.this,
                                    "Receiver hasn’t upgraded the app yet.",Toast.LENGTH_LONG).show();
                            finish(); return;
                        }
                        attachMessageListener();      // ✅ now keys are ready
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { }
                });

        /* -------- meta / recency (unchanged) -------- */
        long now = System.currentTimeMillis();
        String myPhone = FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();

        rootUsers.child(senderId ).child("chats").child(receiverId).child("meta")
                .setValue(new User(receiverId, receiverPhone, receiverName, now));
        rootUsers.child(senderId ).child("chats").child(receiverId).child("lastTimestamp")
                .setValue(now);

        rootUsers.child(receiverId).child("chats").child(senderId ).child("meta")
                .setValue(new User(senderId, myPhone, "You", now));
        rootUsers.child(receiverId).child("chats").child(senderId ).child("lastTimestamp")
                .setValue(now);

        /* -------- UI -------- */
        messageRV    = findViewById(R.id.messageRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendBtn      = findViewById(R.id.sendBtn);

        adapter = new MessageAdapter(new ArrayList<>());
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        messageRV.setLayoutManager(lm);
        messageRV.setAdapter(adapter);

        /* -------- send button -------- */
        sendBtn.setOnClickListener(v -> sendMessage());
    }

    /* ------------------------------------------------------------------ */
    private void attachMessageListener() {

        myMsgRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {

                messages.clear();
                for (DataSnapshot ds : snap.getChildren()) {

                    String cipher64 = ds.child("cipher").getValue(String.class);
                    String iv64     = ds.child("iv").getValue(String.class);
                    String sId      = ds.child("senderId").getValue(String.class);
                    Long   ts       = ds.child("timestamp").getValue(Long.class);
                    if (cipher64==null || iv64==null || sId==null || ts==null) continue;

                    try {
                        /* always derive with the other party’s pubKey */
                        byte[] shared = CryptoHelper.deriveSharedKey(receiverPubKey);
                        String plain  = CryptoAES.decrypt(cipher64, iv64, shared);
                        messages.add(new Message(ds.getKey(), sId, plain, ts));

                    } catch (GeneralSecurityException ignored) { /* bad msg */ }
                    catch (Exception e) { e.printStackTrace(); }
                }

                messages.sort((a,b)-> Long.compare(a.getTimestamp(), b.getTimestamp()));
                adapter.setMessages(messages);
                messageRV.scrollToPosition(adapter.getItemCount()-1);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }

    /* ------------------------------------------------------------------ */
    private void sendMessage() {

        String plain = messageInput.getText().toString().trim();
        if (plain.isEmpty()) return;
        if (receiverPubKey == null) {
            Toast.makeText(this,"Still loading key…",Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            byte[] shared = CryptoHelper.deriveSharedKey(receiverPubKey);
            CryptoAES.Bundle enc = CryptoAES.encrypt(plain, shared);

            String id = UUID.randomUUID().toString();
            long   ts = System.currentTimeMillis();

            Map<String,Object> map = new HashMap<>();
            map.put("cipher",   enc.cipher64);
            map.put("iv",       enc.iv64);
            map.put("senderId", senderId);
            map.put("timestamp",ts);

            myMsgRef  .child(id).setValue(map);
            yourMsgRef.child(id).setValue(map);

            /* update recency */
            rootUsers.child(senderId ).child("chats").child(receiverId).child("lastTimestamp")
                    .setValue(ts);
            rootUsers.child(receiverId).child("chats").child(senderId ).child("lastTimestamp")
                    .setValue(ts);

            messageInput.setText("");

        } catch (Exception e) {
            Toast.makeText(this,"Encrypt error: "+e,Toast.LENGTH_LONG).show();
        }
    }
}
