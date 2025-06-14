package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.adapter.ChatAdapter;
import com.example.securesamvad.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView chatRV;
    private ChatAdapter  chatAdapter;
    private final List<User> chats = new ArrayList<>();

    private FirebaseUser me;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(v,i)->{
            Insets b=i.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left,b.top,b.right,b.bottom); return i;
        });

        me = FirebaseAuth.getInstance().getCurrentUser();

        chatRV = findViewById(R.id.chatRecyclerView);
        chatRV.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(this);
        chatRV.setAdapter(chatAdapter);

        loadRecents();

        FloatingActionButton fab = findViewById(R.id.fabStartChat);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, AllUsersActivity.class)));

        EditText search = findViewById(R.id.searchBar);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                chatAdapter.filter(s.toString());
            }
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
        });
    }

    private void loadRecents() {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(me.getUid())
                .child("chats");

        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<User> temp = new ArrayList<>();
                for (DataSnapshot node : snap.getChildren()) {
                    User u  = node.child("meta").getValue(User.class);
                    Long ts = node.child("lastTimestamp").getValue(Long.class);
                    if (u != null) {
                        u.setLastTimestamp(ts != null ? ts : 0);
                        temp.add(u);
                    }
                }
                temp.sort((a,b)-> Long.compare(b.getLastTimestamp(), a.getLastTimestamp()));
                chatAdapter.setData(temp);   // <‑‑ refresh adapter
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }


}
