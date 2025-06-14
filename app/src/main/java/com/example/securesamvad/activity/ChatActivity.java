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
import com.example.securesamvad.crypto.CryptoHelper;
import com.example.securesamvad.crypto.CryptoAES;
import com.example.securesamvad.model.Message;
import com.example.securesamvad.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.security.GeneralSecurityException;
import java.util.*;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messageRV;
    private EditText messageInput;
    private ImageView sendBtn;

    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter adapter;

    private String senderId, receiverId;
    private String receiverName, receiverPhone;

    private DatabaseReference myMsgRef, yourMsgRef, rootUsers;

    // E2EE keys
    private String myPubKey;           // Base64
    private String receiverPubKey;     // Base64

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, i) -> {
            Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return i;
        });

        // Firebase IDs
        senderId      = FirebaseAuth.getInstance().getUid();
        receiverId    = getIntent().getStringExtra("receiverId");
        receiverName  = getIntent().getStringExtra("receiverName");
        receiverPhone = getIntent().getStringExtra("receiverPhone");

        if (receiverId == null) { finish(); return; }
        if (receiverName == null)  receiverName  = receiverId;
        if (receiverPhone == null) receiverPhone = "";

        // Set receiver info
        ((TextView) findViewById(R.id.userName)).setText(receiverName);
        ((TextView) findViewById(R.id.number)).setText(receiverPhone);

        // Firebase paths
        rootUsers  = FirebaseDatabase.getInstance().getReference("users");
        myMsgRef   = rootUsers.child(senderId).child("chats").child(receiverId).child("messages");
        yourMsgRef = rootUsers.child(receiverId).child("chats").child(senderId).child("messages");

        // Get my public key
        try {
            myPubKey = CryptoHelper.getMyPublicKey(this);
        } catch (Exception e) {
            Toast.makeText(this, "Key error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish(); return;
        }

        // Get receiver's public key from Firebase
        rootUsers.child(receiverId).child("pubKey")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        receiverPubKey = snapshot.getValue(String.class);
                        if (receiverPubKey == null) {
                            Toast.makeText(ChatActivity.this, "Receiver has no public key!", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }

                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Save meta info (unchanged)
        long now = System.currentTimeMillis();
        String myPhone = FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();

        rootUsers.child(senderId).child("chats").child(receiverId).child("meta")
                .setValue(new User(receiverId, receiverPhone, receiverName, now));
        rootUsers.child(senderId).child("chats").child(receiverId).child("lastTimestamp")
                .setValue(now);

        rootUsers.child(receiverId).child("chats").child(senderId).child("meta")
                .setValue(new User(senderId, myPhone, "You", now));
        rootUsers.child(receiverId).child("chats").child(senderId).child("lastTimestamp")
                .setValue(now);

        // Setup UI
        messageRV    = findViewById(R.id.messageRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendBtn      = findViewById(R.id.sendBtn);

        adapter = new MessageAdapter(new ArrayList<>());
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        messageRV.setLayoutManager(lm);
        messageRV.setAdapter(adapter);

        // --- Listen to messages (with decryption) ---
        myMsgRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (receiverPubKey == null) return; // wait until key is fetched

                messages.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String cipher64 = ds.child("cipher").getValue(String.class);
                    String iv64     = ds.child("iv").getValue(String.class);
                    String sId      = ds.child("senderId").getValue(String.class);
                    Long   ts       = ds.child("timestamp").getValue(Long.class);
                    if (cipher64 == null || iv64 == null || sId == null || ts == null) continue;

                    try {
                        String otherPub = sId.equals(senderId) ? receiverPubKey : myPubKey;
                        byte[] shared   = CryptoHelper.deriveSharedKey(otherPub);
                        String plain    = CryptoAES.decrypt(cipher64, iv64, shared);

                        messages.add(new Message(ds.getKey(), sId, plain, ts));
                    } catch (GeneralSecurityException e) {
                        // ignore invalid messages
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                messages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                adapter.setMessages(messages);
                messageRV.scrollToPosition(adapter.getItemCount() - 1);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // --- Send encrypted message ---
        sendBtn.setOnClickListener(v -> {
            String plain = messageInput.getText().toString().trim();
            if (plain.isEmpty()) return;

            if (receiverPubKey == null) {
                Toast.makeText(this, "Still loading receiver key…", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                byte[] shared = CryptoHelper.deriveSharedKey(receiverPubKey);
                CryptoAES.Bundle encrypted = CryptoAES.encrypt(plain, shared);

                String id = UUID.randomUUID().toString();
                long ts = System.currentTimeMillis();

                Map<String, Object> map = new HashMap<>();
                map.put("cipher",   encrypted.cipher64);
                map.put("iv",       encrypted.iv64);
                map.put("senderId", senderId);
                map.put("timestamp", ts);

                myMsgRef.child(id).setValue(map);
                yourMsgRef.child(id).setValue(map);

                // Update meta info
                rootUsers.child(senderId).child("chats").child(receiverId).child("meta")
                        .setValue(new User(receiverId, receiverPhone, receiverName, ts));
                rootUsers.child(senderId).child("chats").child(receiverId).child("lastTimestamp")
                        .setValue(ts);

                rootUsers.child(receiverId).child("chats").child(senderId).child("meta")
                        .setValue(new User(senderId, myPhone, "You", ts));
                rootUsers.child(receiverId).child("chats").child(senderId).child("lastTimestamp")
                        .setValue(ts);

                messageInput.setText("");

            } catch (Exception e) {
                Toast.makeText(this, "Encrypt error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
