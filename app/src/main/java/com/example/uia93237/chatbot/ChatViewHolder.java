package com.example.uia93237.chatbot;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

class ChatViewHolder extends RecyclerView.ViewHolder {

        TextView leftText,rightText;

        ChatViewHolder(View itemView){
            super(itemView);
            leftText = itemView.findViewById(R.id.leftText);
            rightText = itemView.findViewById(R.id.rightText);

        }

}
