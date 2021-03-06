package la.manga.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import la.manga.app.R;
import la.manga.app.entities.Talk;

public class TalkAdapter extends RecyclerView.Adapter<TalkAdapter.ViewHolder> {
    private final int resourceId;
    private final List<Talk> talks = new ArrayList<>();
    private OnClickListener onClickListener;

    public TalkAdapter(int resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(resourceId, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.tvTitle.setText(talks.get(position).getTitle());

        DateFormat format = SimpleDateFormat.getDateInstance();
        holder.tvDate.setText(format.format(talks.get(position).getDate().getTime()));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notifyOnClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return talks.size();
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void addAll(Collection<Talk> talks) {
        this.talks.addAll(talks);
        notifyDataSetChanged();
    }

    private void notifyOnClick(int position) {
        OnClickListener onClickListener = this.onClickListener;

        if (onClickListener != null)
            onClickListener.onClick(talks.get(position));
    }

    public interface OnClickListener {
        void onClick(Talk talk);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDate;

        public ViewHolder(View itemView) {
            super(itemView);
            tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvDate = (TextView) itemView.findViewById(R.id.tvDate);
        }
    }
}
