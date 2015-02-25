package com.pr0gramm.app;

/**
* Content type to load.
*/
public enum ContentType {
    SFW(1, R.string.type_sfw), NSFW(2, R.string.type_nsfw), NSFL(4, R.string.type_nsfl);

    private final int flag;
    private final int title;

    ContentType(int flag, int desc) {
        this.flag = flag;
        this.title = desc;
    }

    public int getTitle() {
        return title;
    }

    public int getFlag() {
        return flag;
    }

    public static int combine(Iterable<ContentType> flags) {
        int sum = 0;
        for (ContentType flag : flags)
            sum += flag.getFlag();

        return sum;
    }
}
