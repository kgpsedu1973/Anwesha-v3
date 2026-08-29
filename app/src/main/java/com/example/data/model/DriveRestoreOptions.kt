package com.example.data.model

enum class DriveRestoreMode(val titleBn: String, val descBn: String) {
    EXCLUDE_OFFLINE(
        "ক্লিন রিস্টোর (অফলাইন বাদ দিয়ে ড্রাইভ প্রতিস্থাপন)",
        "বর্তমান ডিভাইসের সব ডেটা মুছে শুধুমাত্র ড্রাইভ ব্যাকআপের ডেটা হুবহু প্রতিস্থাপন করবে।"
    ),
    MERGE(
        "মার্জ রিস্টোর (ড্রাইভ ও অফলাইন উভয় ডেটা একত্রীকরণ)",
        "বর্তমান ডেটা না মুছে ড্রাইভের ডেটার সাথে একত্রিত (Merge) করবে।"
    ),
    INCLUDE_OFFLINE(
        "স্মার্ট রিস্টোর (অফলাইন পরিবর্তন রেখে ড্রাইভ ডেটা যোগ)",
        "অফলাইনে পরিবর্তিত রেকর্ড অক্ষুণ্ণ রেখে ড্রাইভ থেকে অনুপস্থিত ও নতুন রেকর্ডসমূহ যুক্ত করবে।"
    )
}

enum class DriveSyncTarget(val titleBn: String) {
    PRIMARY_ONLY("মূল ড্রাইভ (Primary)"),
    SECONDARY_ONLY("দ্বিতীয় ড্রাইভ (Secondary)"),
    BOTH("উভয় ড্রাইভ (Dual Cloud Sync)")
}
