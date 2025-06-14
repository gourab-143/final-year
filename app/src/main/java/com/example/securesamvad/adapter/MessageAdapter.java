package com.example.securesamvad.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.securesamvad.R;
import com.example.securesamvad.model.Message;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter that inserts a date header whenever the day changes
 * and shows time under each bubble.
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Object> rows = new ArrayList<>();   // Message or Long (dateHeader)
    private static final int ITEM_DATE     = 0;
    private static final int ITEM_SENT     = 1;
    private static final int ITEM_RECEIVED = 2;

    private final SimpleDateFormat timeFmt  = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFmt  = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final String myUid = FirebaseAuth.getInstance().getUid();

    public MessageAdapter(List<Message> rawMessages) { setMessages(rawMessages); }

    /** call whenever ChatActivity updates its message list */
    public void setMessages(List<Message> raw) {
        rows.clear();
        long lastDay = -1;
        for (Message m : raw) {
            long day = stripToDay(m.getTimestamp());
            if (day != lastDay) {
                rows.add(day);          // date header
                lastDay = day;
            }
            rows.add(m);
        }
        notifyDataSetChanged();
    }

    private long stripToDay(long ts) { return ts / (24*60*60*1000L); }

    /* -------- view‑type logic -------- */
    @Override public int getItemViewType(int pos) {
        Object o = rows.get(pos);
        if (o instanceof Long) return ITEM_DATE;
        Message m = (Message) o;
        return m.getSenderId().equals(myUid) ? ITEM_SENT : ITEM_RECEIVED;
    }

    /* -------- create -------- */
    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int type){
        LayoutInflater inf = LayoutInflater.from(p.getContext());
        if (type == ITEM_DATE) {
            View v = inf.inflate(R.layout.item_date_header, p, false);
            return new DateVH(v);
        } else if (type == ITEM_SENT) {
            View v = inf.inflate(R.layout.item_sent, p, false);
            return new SentVH(v);
        } else {
            View v = inf.inflate(R.layout.item_received, p, false);
            return new RecVH(v);
        }
    }

    /* -------- bind -------- */
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int pos){
        int type = getItemViewType(pos);
        if (type == ITEM_DATE) {
            long day = (Long) rows.get(pos);
            ((DateVH)h).date.setText(dateFmt.format(new Date(day*24*60*60*1000L)));
        } else if (type == ITEM_SENT) {
            Message m = (Message) rows.get(pos);
            ((SentVH)h).text.setText(m.getMessage());
            ((SentVH)h).time.setText(timeFmt.format(new Date(m.getTimestamp())));
        } else {
            Message m = (Message) rows.get(pos);
            ((RecVH)h).text.setText(m.getMessage());
            ((RecVH)h).time.setText(timeFmt.format(new Date(m.getTimestamp())));
        }
    }

    @Override public int getItemCount(){ return rows.size(); }

    /* -------- viewholders -------- */
    static class DateVH extends RecyclerView.ViewHolder {
        TextView date; DateVH(View v){ super(v); date = v.findViewById(R.id.dateText); }
    }
    static class SentVH extends RecyclerView.ViewHolder {
        TextView text,time;
        SentVH(View v){ super(v);
            text = v.findViewById(R.id.messageText);
            time = v.findViewById(R.id.timeText);
        }
    }
    static class RecVH extends RecyclerView.ViewHolder {
        TextView text,time;
        RecVH(View v){ super(v);
            text = v.findViewById(R.id.messageText);
            time = v.findViewById(R.id.timeText);
        }
    }
}
