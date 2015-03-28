create table if not exists bookmark (
    ID integer primary key autoincrement,
    TITLE text not null,
    FILTER_FEED_TYPE text not null,
    FILTER_TAGS text,
    FILTER_USERNAME text
);
