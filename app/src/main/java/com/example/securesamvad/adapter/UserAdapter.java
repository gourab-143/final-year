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

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserVH> {

    private final Context context;
    private final List<User> users;

    public UserAdapter(Context c, List<User> list) {
        this.context = c;
        this.users   = list;
    }

    @NonNull @Override
    public UserVH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_recent, p, false);
        return new UserVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserVH h, int pos) {
        User u = users.get(pos);
        h.name.setText(u.getName()  != null ? u.getName()  : u.getPhone());
        h.last.setText(u.getPhone());

        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(context, ChatActivity.class);
            i.putExtra("receiverId",   u.getUid());
            i.putExtra("receiverName", u.getName());
            i.putExtra("receiverPhone", u.getPhone());   // NEW
            context.startActivity(i);
        });
    }
    @Override public int getItemCount() { return users.size(); }

    static class UserVH extends RecyclerView.ViewHolder {
        TextView name, last;
        UserVH(View v) {
            super(v);
            name  = v.findViewById(R.id.textUserName);
            last = v.findViewById(R.id.textLast);
        }
    }
}
