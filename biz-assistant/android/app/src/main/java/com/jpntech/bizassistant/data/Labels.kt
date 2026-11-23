package com.jpntech.bizassistant.data

/** 코드값을 화면에 보여줄 한국어 이름으로 바꾼다. 서버 스키마(relay-server/lib/schema.ts)와 짝이다. */
object Labels {
    val types: List<Pair<String, String>> = listOf(
        "fault" to "장애·AS 접수", "inspection" to "점검", "delivery" to "납품·설치", "acceptance" to "검수·준공",
        "document" to "서류 제출", "quote" to "견적·입찰", "contract" to "계약", "payment" to "대금·기성",
        "material" to "자재·외주", "nightwork" to "야간 작업", "meeting" to "회의·방문", "info" to "정보 공유", "personal" to "사업 무관",
    )
    val urgencies: List<Pair<String, String>> = listOf("high" to "높음", "normal" to "보통", "low" to "낮음")
    val orgKinds: List<Pair<String, String>> = listOf(
        "client" to "발주처", "partner" to "협력업체", "vendor" to "자재·외주", "internal" to "내부", "other" to "기타",
    )
    val systems: List<Pair<String, String>> = listOf(
        "power_monitoring" to "전력감시", "lighting_control" to "조명제어", "both" to "전력감시+조명제어", "none" to "해당 없음",
    )
    val owners: List<Pair<String, String>> = listOf("me" to "나", "counterpart" to "상대", "partner" to "협력업체")
    val moneyKinds: List<Pair<String, String>> = listOf(
        "receivable" to "받을 돈", "payable" to "줄 돈", "quote" to "견적", "contract" to "계약",
    )
    val sources: List<Pair<String, String>> = listOf(
        "call" to "통화", "sms" to "문자", "kakao" to "카톡", "mail" to "메일", "visit" to "방문", "memo" to "메모",
    )
    val ticketStatuses: List<Pair<String, String>> = listOf(
        "open" to "접수", "scheduled" to "방문 예정", "working" to "조치 중", "done" to "완료",
    )

    fun type(code: String) = types.firstOrNull { it.first == code }?.second ?: code
    fun urgency(code: String) = urgencies.firstOrNull { it.first == code }?.second ?: code
    fun orgKind(code: String) = orgKinds.firstOrNull { it.first == code }?.second ?: code
    fun system(code: String) = systems.firstOrNull { it.first == code }?.second ?: code
    fun owner(code: String) = owners.firstOrNull { it.first == code }?.second ?: code
    fun moneyKind(code: String) = moneyKinds.firstOrNull { it.first == code }?.second ?: code
    fun source(code: String) = sources.firstOrNull { it.first == code }?.second ?: code
    fun ticketStatus(code: String) = ticketStatuses.firstOrNull { it.first == code }?.second ?: code

    fun won(amount: Long?): String = if (amount == null) "금액 미정" else "%,d원".format(amount)
}
