package com.example.securesamvad.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.activity.ChatActivity;
import com.example.securesamvad.model.Contact;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.CVH> {

    private final Context context;

    /* master list & visible list */
    private final List<Contact> master = new ArrayList<>();
    private final List<Contact> shown  = new ArrayList<>();

    public ContactAdapter(Context ctx, List<Contact> initial) {
        context = ctx;
        setData(initial);
    }

    /* -------- external data refresh -------- */
    public void setData(List<Contact> list) {
        master.clear();
        master.addAll(list);
        shown.clear();
        shown.addAll(list);
        notifyDataSetChanged();
    }

    /* -------- search filter -------- */
    public void filter(String query) {
        shown.clear();
        if (query == null || query.trim().isEmpty()) {
            shown.addAll(master);
        } else {
            String q = query.toLowerCase();
            for (Contact c : master) {
                if (c.getName().toLowerCase().contains(q) ||
                        c.getPhone().contains(q)) {
                    shown.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    /* -------- RecyclerView -------- */
    @NonNull @Override
    public CVH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_contact, p, false);
        return new CVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CVH h, int pos) {
        Contact c = shown.get(pos);
        h.name .setText(c.getName());
        h.phone.setText(c.getPhone());
        h.status.setText(c.isRegistered() ? "On SecureSamvad" : "Invite");

        h.itemView.setOnClickListener(v -> {
            if (c.isRegistered()) {
                Intent i = new Intent(context, ChatActivity.class);
                i.putExtra("receiverId",    c.getUid());
                i.putExtra("receiverName",  c.getName());
                i.putExtra("receiverPhone", c.getPhone());
                context.startActivity(i);
            } else {
                Toast.makeText(context,
                        c.getName() + " is not on SecureSamvad yet",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public int getItemCount() { return shown.size(); }

    /* ViewHolder */
    static class CVH extends RecyclerView.ViewHolder {
        TextView name, phone, status;
        CVH(View v) {
            super(v);
            name   = v.findViewById(R.id.textName);
            phone  = v.findViewById(R.id.textPhone);
            status = v.findViewById(R.id.textStatus);
        }
    }
}
