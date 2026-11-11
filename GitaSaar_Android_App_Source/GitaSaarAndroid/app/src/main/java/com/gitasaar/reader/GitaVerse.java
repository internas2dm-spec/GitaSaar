package com.gitasaar.reader;

public final class GitaVerse {
    public final int chapter;
    public final int number;
    public final String chapterName;
    public final String sanskrit;
    public final String hindi;

    public GitaVerse(int chapter, int number, String chapterName, String sanskrit, String hindi) {
        this.chapter = chapter;
        this.number = number;
        this.chapterName = chapterName;
        this.sanskrit = sanskrit;
        this.hindi = hindi;
    }

    public String id() { return chapter + "-" + number; }
    public String reference() { return "अध्याय " + chapter + " · श्लोक " + number; }
}
