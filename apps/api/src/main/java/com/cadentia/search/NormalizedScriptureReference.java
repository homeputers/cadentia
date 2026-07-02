package com.cadentia.search;

public record NormalizedScriptureReference(String book, int chapter, Integer startVerse, Integer endVerse) {

    public boolean matches(NormalizedScriptureReference query) {
        if (!book.equals(query.book()) || chapter != query.chapter()) {
            return false;
        }
        if (query.startVerse() == null) {
            return true;
        }
        if (startVerse == null) {
            return false;
        }
        int queryEnd = query.endVerse() == null ? query.startVerse() : query.endVerse();
        int documentEnd = endVerse == null ? startVerse : endVerse;
        return startVerse <= queryEnd && documentEnd >= query.startVerse();
    }

    public String display() {
        if (startVerse == null) {
            return book + " " + chapter;
        }
        if (endVerse == null || endVerse.equals(startVerse)) {
            return book + " " + chapter + ":" + startVerse;
        }
        return book + " " + chapter + ":" + startVerse + "-" + endVerse;
    }
}
