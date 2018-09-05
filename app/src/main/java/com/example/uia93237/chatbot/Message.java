package com.example.uia93237.chatbot;

import java.util.Date;


@SuppressWarnings("unused")
public class Message {

    /*
    Bean class that gets stored on Firebase Real-Time DB and represents a message sent in the chat room by either the bot or the user
     */

    private String msgText;
    private String msgUser;
    private long timestamp;

    // No-arg constructor needed for Firebase
    public Message(){

        this.timestamp = new Date().getTime();
    }

    Message(String msgText, String msgUser) {
        this.msgText = msgText;
        this.msgUser = msgUser;
        this.timestamp = new Date().getTime();
    }

    public String getMsgText() {

        return msgText;
    }

    public void setMsgText(String msgText) {

        this.msgText = msgText;
    }

    public String getMsgUser() {

        return msgUser;
    }

    public void setMsgUser(String msgUser) {

        this.msgUser = msgUser;
    }

    public long getTimestamp() {

        return timestamp;
    }

    public void setTimestamp(long timestamp) {

        this.timestamp = timestamp;
    }
}
