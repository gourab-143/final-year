package  com.example.securesamvad.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.adapter.ContactAdapter;
import com.example.securesamvad.model.Contact;
import com.example.securesamvad.model.User;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllUsersActivity extends AppCompatActivity {

    private ContactAdapter adapter;
    private final List<Contact> contactList = new ArrayList<>();

    private RecyclerView rv;
    private EditText searchBar;

    /* permission launcher */
    private final ActivityResultLauncher<String> askPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    g -> { if (g) loadContacts(); else finish(); });

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_all_users);

        rv = findViewById(R.id.recyclerViewAllUsers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactAdapter(this, contactList);
        rv.setAdapter(adapter);

        searchBar = findViewById(R.id.searchContacts);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                adapter.filter(s.toString());
            }
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else askPerm.launch(Manifest.permission.READ_CONTACTS);
    }

    /* ---------------- load contacts + Firebase match ---------------- */
    private void loadContacts() {
        Map<String, Contact> map = new HashMap<>();

        Cursor cur = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);

        if (cur != null) {
            while (cur.moveToNext()) {
                String name  = cur.getString(0);
                String phone = cur.getString(1)
                        .replaceAll("\\s|[-()]", "")
                        .replaceFirst("^0", "+91");
                map.put(phone, new Contact(name != null ? name : phone, phone));
            }
            cur.close();
        }

        FirebaseDatabase.getInstance().getReference("users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot ds : snap.getChildren()) {
                            User u = ds.getValue(User.class);
                            if (u == null) continue;
                            String p = u.getPhone();
                            if (map.containsKey(p)) {
                                Contact c = map.get(p);
                                c.setRegistered(true);
                                c.setUid(u.getUid());
                            }
                        }
                        contactList.clear();
                        contactList.addAll(map.values());
                        adapter.setData(contactList);            // refresh list
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        Toast.makeText(AllUsersActivity.this,
                                "Firebase error: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
