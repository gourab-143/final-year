package com.example.securesamvad.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.securesamvad.R;
import com.example.securesamvad.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    /* ---------- UI ---------- */
    private CircleImageView avatar;
    private TextView        nameText, statusText;

    /* ---------- Firebase ---------- */
    private String            uid;
    private DatabaseReference userNode;
    private StorageReference  avatarBucket;

    /* ---------- result launchers ---------- */
    // FIRST: declare pickImage
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::uploadPickedImage);

    // THEN: declare askImagePerm (after pickImage exists)
    private final ActivityResultLauncher<String> askImagePerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) pickImage.launch("image/*"); });


    /* ------------------------------------------------------------ */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        /* edge‑to‑edge padding */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(v,i)->{
            Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left,s.top,s.right,s.bottom); return i; });

        /* bind */
        avatar     = findViewById(R.id.profileImage);
        nameText   = findViewById(R.id.textName);
        statusText = findViewById(R.id.textStatus);

        /* firebase refs */
        uid         = FirebaseAuth.getInstance().getUid();
        userNode    = FirebaseDatabase.getInstance().getReference("users").child(uid);
        avatarBucket= FirebaseStorage.getInstance().getReference("avatars");

        /* listeners */
        avatar   .setOnClickListener(v -> requestPickImage());
        nameText .setOnClickListener(v -> promptEdit("Change name",   nameText.getText().toString(),   "name"));
        statusText.setOnClickListener(v -> promptEdit("Change status", statusText.getText().toString(), "status"));

        loadProfile();
    }

    /* ---------- load current values ---------- */
    private void loadProfile() {
        userNode.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                User u = snap.getValue(User.class);
                if (u == null) return;

                if (u.getName()    != null) nameText.setText(u.getName());
                if (u.getStatus()  != null) statusText.setText(u.getStatus());
                if (u.getPhotoUrl()!= null && !u.getPhotoUrl().isEmpty())
                    Glide.with(ProfileActivity.this)
                            .load(u.getPhotoUrl())
                            .placeholder(R.drawable.app_logo)
                            .into(avatar);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { }
        });
    }

    /* ---------- choose image ---------- */
    private void requestPickImage() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, perm)
                == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*");
        } else askImagePerm.launch(perm);
    }

    private void uploadPickedImage(Uri uri) {
        if (uri == null) return;

        Glide.with(this).load(uri).into(avatar);             // instant preview

        /* always overwrite same file → no bucket clutter */
        StorageReference dst = avatarBucket.child(uid + ".jpg");

        dst.putFile(uri)
                .continueWithTask(t -> dst.getDownloadUrl())
                .addOnSuccessListener(link ->
                        propagateField("photoUrl", link.toString()))
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Upload failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }


    /* ---------- update text field ---------- */
    private void promptEdit(String title, String current, String field) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(current);
        input.setSelection(current.length());

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String txt = input.getText().toString().trim();
                    if (field.equals("name"))   nameText.setText(txt);
                    if (field.equals("status")) statusText.setText(txt);
                    propagateField(field, txt);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ---------- helper to write value for me + my contacts ---------- */
    private void propagateField(String key, String value) {
        // 1. update my own profile
        userNode.child(key).setValue(value);

        // 2. push to everyone I chatted with so their "recent list" shows new data
        FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot peer : snap.getChildren()) {
                            String peerId = peer.getKey();
                            if (peerId == null) continue;

                            DatabaseReference meta = FirebaseDatabase.getInstance()
                                    .getReference("users")
                                    .child(peerId)
                                    .child("chats")
                                    .child(uid)
                                    .child("meta")
                                    .child(key);

                            meta.setValue(value);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { }
                });
    }
}
