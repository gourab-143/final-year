package com.example.securesamvad.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.activity.ChatActivity;
import com.example.securesamvad.model.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatVH> {

    private final Context context;

    /** master list (for search) */
    private final List<User> master = new ArrayList<>();
    /** filtered / visible list */
    private final List<User> shown  = new ArrayList<>();

    private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    public ChatAdapter(Context c) { this.context = c; }

    /* called by MainActivity whenever Firebase data changes */
    public void setData(List<User> newData) {
        master.clear();
        master.addAll(newData);
        shown.clear();
        shown.addAll(newData);
        notifyDataSetChanged();
    }

    /* simple search filter */
    public void filter(String q) {
        shown.clear();
        if (q == null || q.trim().isEmpty()) {
            shown.addAll(master);
        } else {
            String lower = q.toLowerCase();
            for (User u : master) {
                if ((u.getName()!=null && u.getName().toLowerCase().contains(lower)) ||
                        (u.getPhone()!=null && u.getPhone().contains(lower))) {
                    shown.add(u);
                }
            }
        }
        notifyDataSetChanged();
    }

    /* ------------ RecyclerView overrides ------------ */
    @NonNull @Override
    public ChatVH onCreateViewHolder(@NonNull ViewGroup parent,int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_recent, parent, false); // ✅ use item_recent
        return new ChatVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatVH h,int pos) {
        User u = shown.get(pos);

        h.name.setText(u.getName()!=null && !u.getName().isEmpty()
                ? u.getName() : u.getPhone());
        h.last.setText(u.getPhone());  // you can replace with last‑message preview
        h.time.setText(timeFmt.format(u.getLastTimestamp()));

        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(context, ChatActivity.class);
            i.putExtra("receiverId",    u.getUid());
            i.putExtra("receiverName",  u.getName());
            i.putExtra("receiverPhone", u.getPhone());
            context.startActivity(i);
        });
    }

    @Override public int getItemCount() { return shown.size(); }

    /* ------------ ViewHolder ------------ */
    static class ChatVH extends RecyclerView.ViewHolder {
        TextView name, last, time;
        ChatVH(View v) {
            super(v);
            name = v.findViewById(R.id.textUserName);
            last = v.findViewById(R.id.textLast);
            time = v.findViewById(R.id.textTime);
        }
    }
}
