package com.example.gesture.in.entity;

public class NewInfo {
    private String userName;
    private String tag;
    private String title;
    private String summary;

    public NewInfo(String userName, String tag, String title, String summary) {
        this.userName = userName;
        this.tag = tag;
        this.title = title;
        this.summary = summary;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
