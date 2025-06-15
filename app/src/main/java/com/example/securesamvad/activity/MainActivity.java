package com.example.securesamvad.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

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

/** Home – recent chats, search bar, FAB and a PopupMenu (Profile · Refresh · Logout). */
public class MainActivity extends AppCompatActivity {

    private RecyclerView chatRV;
    private ChatAdapter  chatAdapter;
    private FirebaseUser me;

    /* ---------------------------------------------------------------- */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        /* edge‑to‑edge paddings */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(v,i)->{
            Insets b=i.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left,b.top,b.right,b.bottom); return i; });

        me = FirebaseAuth.getInstance().getCurrentUser();

        /* -------- RecyclerView -------- */
        chatRV = findViewById(R.id.chatRecyclerView);
        chatRV.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(this);
        chatRV.setAdapter(chatAdapter);

        loadRecents();                         // first load

        /* -------- FAB – start new chat -------- */
        FloatingActionButton fab = findViewById(R.id.fabStartChat);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, AllUsersActivity.class)));

        /* -------- search filtering -------- */
        EditText search = findViewById(R.id.searchBar);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                chatAdapter.filter(s.toString());
            }
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
        });

        /* -------- 3‑dot menu -------- */
        ImageButton menuBtn = findViewById(R.id.btnMenu);
        menuBtn.setOnClickListener(v -> showPopupMenu(v));
    }

    /* ---------------------------------------------------------------- */
    /** Pulls recent chats ordered by lastTimestamp DESC */
    private void loadRecents() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(me.getUid())
                .child("chats");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<User> list = new ArrayList<>();
                for (DataSnapshot d : snap.getChildren()) {
                    User u  = d.child("meta").getValue(User.class);
                    Long ts = d.child("lastTimestamp").getValue(Long.class);
                    if (u!=null){
                        u.setLastTimestamp(ts!=null ? ts : 0);
                        list.add(u);
                    }
                }
                list.sort((a,b)-> Long.compare(b.getLastTimestamp(), a.getLastTimestamp()));
                chatAdapter.setData(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        loadRecents();         // refresh when coming back (e.g. after profile edit)
    }

    /* ---------------------------------------------------------------- */
    private void showPopupMenu(android.view.View anchor) {
        PopupMenu pop = new PopupMenu(this, anchor);
        pop.getMenuInflater().inflate(R.menu.main, pop.getMenu());   // main.xml with profile/refresh/logout
        pop.setOnMenuItemClickListener(this::handleMenuClick);
        pop.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;

        } else if (id == R.id.menu_refresh) {
            loadRecents();
            return true;

        } else if (id == R.id.menu_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, SplashActivity.class));
            finishAffinity();
            return true;
        }
        return false;
    }

}
