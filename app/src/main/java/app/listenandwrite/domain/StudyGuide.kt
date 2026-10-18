package app.listenandwrite.domain

/**
 * 语文按易混字分组，英语按同音词分组。
 * 讲解写在本地，听写时不联网。词库里没有的词，家长可以自己补释义。
 */
data class GuideHit(
    val meaning: String = "",
    val contrast: String = "",
    val focus: String = "",
    val groupTip: String = "",
    val companions: List<String> = emptyList(),
)

object StudyGuide {
    fun lookup(text: String, lang: Lang): GuideHit? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        return if (lang == Lang.EN) lookupEnglish(clean) else lookupChinese(clean)
    }

    fun missingCompanions(text: String, lang: Lang, already: Set<String>): List<String> {
        val hit = lookup(text, lang) ?: return emptyList()
        val have = already.map { it.trim() }.toSet()
        return hit.companions.filter { it.isNotBlank() && it != text.trim() && it !in have }.distinct()
    }

    fun focusLabel(focus: String): String {
        if (focus.isBlank()) return ""
        return if (focus.any { isLatin(it) }) "同音" else focus
    }

    private fun lookupChinese(text: String): GuideHit? {
        val found = text.flatMap { ch -> families.mapNotNull { family -> family.member(ch)?.let { family to it } } }
            .distinctBy { it.second.character }
            .take(2)
        val meaning = meanings[text].orEmpty()
        if (found.isEmpty() && meaning.isEmpty()) return null
        val primary = found.firstOrNull()
        return GuideHit(
            meaning = meaning,
            contrast = found.joinToString("") { it.second.say },
            focus = primary?.second?.character?.toString().orEmpty(),
            groupTip = primary?.first?.tip.orEmpty(),
            companions = primary?.second?.examples.orEmpty().filter { it != text },
        )
    }

    private fun lookupEnglish(text: String): GuideHit? {
        val key = text.lowercase()
        val entry = english[key] ?: return null
        return GuideHit(
            meaning = entry.meaning,
            contrast = entry.avoid,
            focus = entry.family,
            groupTip = entry.groupTip,
            companions = entry.companions,
        )
    }

    private fun isLatin(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z'

    private data class Member(val character: Char, val say: String, val examples: List<String>)
    private data class Family(val tip: String, val members: List<Member>) {
        fun member(ch: Char): Member? = members.find { it.character == ch }
    }

    private data class En(
        val meaning: String,
        val avoid: String = "",
        val family: String = "",
        val groupTip: String = "",
        val companions: List<String> = emptyList(),
    )

    private fun m(ch: Char, say: String, vararg examples: String) = Member(ch, say, examples.toList())

    private fun f(tip: String, vararg members: Member) = Family(tip, members.toList())

    private val families = listOf(
        f(
            "势是气势和力量，事是事情。势不可挡的势，不要写成事情的事。",
            m('势', "这里的势，是气势的势，不是事情的事。", "势不可挡", "气势", "优势", "势均力敌"),
            m('事', "这里的事，是事情的事，不是气势的势。", "事情", "故事", "事先"),
        ),
        f(
            "做是做事、做人。作是作业、工作、作用。",
            m('做', "这里的做，是做事的做，不是作业的作。", "做事", "做人", "做工"),
            m('作', "这里的作，是作业的作，不是做事的做。", "作业", "工作", "作用"),
        ),
        f(
            "在是存在、现在。再是又一次。",
            m('在', "这里的在，是现在的在，不是再次的再。", "现在", "正在", "存在"),
            m('再', "这里的再，是再次的再，不是现在的在。", "再次", "再三", "再见"),
        ),
        f(
            "像是好像。象是大象，也用于气象、印象。",
            m('像', "这里的像，是好像的像，不是大象的象。", "好像", "画像", "像样"),
            m('象', "这里的象，是大象的象，不是好像的像。", "大象", "气象", "印象"),
        ),
        f(
            "己是自己。已是已经。两个字开口的方向不一样。",
            m('己', "这里的己，是自己的己，不是已经的已。", "自己", "知己"),
            m('已', "这里的已，是已经的已，不是自己的己。", "已经"),
        ),
        f(
            "辨是辨别，中间是一点一撇。辩是辩论，中间是言。辫是辫子，中间是纟。",
            m('辨', "这里的辨，是辨别的辨，不是辩论的辩，也不是辫子的辫。", "辨别", "分辨"),
            m('辩', "这里的辩，是辩论的辩，不是辨别的辨。", "辩论", "辩解"),
            m('辫', "这里的辫，是辫子的辫，不是辨别的辨。", "辫子"),
        ),
        f(
            "燥是干燥，旁边是火。躁是急躁，旁边是足。",
            m('燥', "这里的燥，是干燥的燥，不是急躁的躁。", "干燥", "枯燥"),
            m('躁', "这里的躁，是急躁的躁，不是干燥的燥。", "急躁", "烦躁", "暴躁"),
        ),
        f(
            "坐是坐下这个动作。座是座位，是坐的地方。",
            m('坐', "这里的坐，是坐下的坐，不是座位的座。", "坐下", "请坐"),
            m('座', "这里的座，是座位的座，不是坐下的坐。", "座位", "讲座"),
        ),
        f(
            "密是秘密。蜜是蜜蜂、蜂蜜，下面有虫。",
            m('密', "这里的密，是秘密的密，不是蜂蜜的蜜。", "秘密", "保密", "亲密"),
            m('蜜', "这里的蜜，是蜂蜜的蜜，不是秘密的密。", "蜜蜂", "蜂蜜", "甜蜜"),
        ),
        f(
            "戴是戴在头上、手上。带是带着、皮带。",
            m('戴', "这里的戴，是戴帽子的戴，不是带着的带。", "爱戴"),
            m('带', "这里的带，是带着的带，不是戴帽子的戴。", "带领", "皮带", "带走"),
        ),
        f(
            "需是需要，上面是雨。须是必须，左边是彡。",
            m('需', "这里的需，是需要的需，不是必须的须。", "需要", "需求"),
            m('须', "这里的须，是必须的须，不是需要的需。", "必须", "须知"),
        ),
        f(
            "即是立即、即使。既是既然。",
            m('即', "这里的即，是立即的即，不是既然的既。", "即使", "立即"),
            m('既', "这里的既，是既然的既，不是立即的即。", "既然"),
        ),
        f(
            "未是未来，一横短。末是末尾，一横长。",
            m('未', "这里的未，是未来的未，不是末尾的末。", "未来", "未知"),
            m('末', "这里的末，是末尾的末，不是未来的未。", "末尾", "周末"),
        ),
        f(
            "竞是竞争。竟是竟然。",
            m('竞', "这里的竞，是竞争的竞，不是竟然的竟。", "竞争", "竞赛"),
            m('竟', "这里的竟，是竟然的竟，不是竞争的竞。", "竟然", "究竟"),
        ),
        f(
            "厉是严厉、再接再厉。历是历史、经历。",
            m('厉', "这里的厉，是严厉的厉，不是历史的历。", "严厉", "再接再厉", "厉害"),
            m('历', "这里的历，是历史的历，不是严厉的厉。", "历史", "经历", "日历"),
        ),
        f(
            "慕是羡慕，下面是心。幕是开幕、夜幕，下面是巾。",
            m('慕', "这里的慕，是羡慕的慕，不是开幕的幕。", "羡慕", "爱慕"),
            m('幕', "这里的幕，是开幕的幕，不是羡慕的慕。", "开幕", "夜幕", "屏幕"),
        ),
        f(
            "刻是时刻、刻苦。克是克服。",
            m('刻', "这里的刻，是时刻的刻，不是克服的克。", "立刻", "刻苦", "时刻"),
            m('克', "这里的克，是克服的克，不是时刻的刻。", "克服", "克制"),
        ),
        f(
            "拔是拔出来。拨是拨电话，旁边是扌，右边是发。",
            m('拔', "这里的拔，是拔河的拔，不是拨打的拨。", "拔河"),
            m('拨', "这里的拨，是拨打的拨，不是拔河的拔。", "拨打", "点拨"),
        ),
        f(
            "折是折纸。拆是拆开，右边是斥。",
            m('折', "这里的折，是折纸的折，不是拆开的拆。", "折纸", "曲折"),
            m('拆', "这里的拆，是拆开的拆，不是折纸的折。", "拆开", "拆除"),
        ),
    )

    private val meanings = mapOf(
        "势不可挡" to "来势很猛，挡也挡不住。",
        "气势" to "表现出来的力量。",
        "优势" to "比别人强的地方。",
        "势均力敌" to "两边力量差不多。",
        "事情" to "发生的一件事。",
        "故事" to "讲给人听的事。",
        "事先" to "事情发生以前。",
        "一心一意" to "一门心思做一件事。",
        "春暖花开" to "春天暖和，花开了。",
        "万众一心" to "很多人心往一处想。",
        "守株待兔" to "死守旧办法，想不劳而获。",
        "画蛇添足" to "多做了没用的事。",
        "亡羊补牢" to "出了问题赶快补救。",
        "井底之蛙" to "见识太少。",
        "狐假虎威" to "仗着别人的势力吓人。",
        "掩耳盗铃" to "自己骗自己。",
        "自相矛盾" to "前后说法打架。",
        "刻舟求剑" to "不知道情况已经变了。",
        "南辕北辙" to "做的事和目的相反。",
        "惊弓之鸟" to "受过惊吓，一点动静就害怕。",
        "卧薪尝胆" to "吃苦也要发奋。",
        "望梅止渴" to "用空想安慰自己。",
        "全神贯注" to "注意力非常集中。",
        "聚精会神" to "精神很集中。",
        "一丝不苟" to "做事非常认真。",
        "再接再厉" to "一次之后继续努力。",
        "不可思议" to "难以想象。",
        "半途而废" to "做到一半就停下。",
        "春天" to "一年里变暖、植物生长的季节。",
        "美丽" to "好看。",
        "认真" to "仔细，不马虎。",
        "花朵" to "植物开的花。",
        "做事" to "做一件事。",
        "做人" to "怎样当一个人。",
        "作业" to "老师留的练习。",
        "工作" to "要完成的事。",
        "现在" to "这个时候。",
        "正在" to "动作正在进行。",
        "再次" to "又一次。",
        "自己" to "本人。",
        "已经" to "完成了。",
        "辨别" to "分清楚。",
        "辩论" to "各自讲道理。",
        "干燥" to "没有水分。",
        "急躁" to "心里着急，沉不住气。",
        "秘密" to "不告诉别人的事。",
        "蜂蜜" to "蜜蜂酿的糖。",
        "需要" to "一定要有。",
        "必须" to "一定要这样做。",
        "未来" to "还没有到的时候。",
        "末尾" to "最后的部分。",
        "竞争" to "比谁更好。",
        "竟然" to "没想到会这样。",
        "严厉" to "很严格。",
        "历史" to "过去发生的事。",
        "羡慕" to "希望自己也有。",
        "克服" to "把困难战胜。",
        "好像" to "看起来像。",
        "大象" to "很大的动物。",
    )

    private val english: Map<String, En> = buildMap {
        simple(
            "apple" to "苹果",
            "banana" to "香蕉",
            "orange" to "橙子",
            "grape" to "葡萄",
            "school" to "学校",
            "teacher" to "老师",
            "student" to "学生",
            "book" to "书",
            "pen" to "钢笔",
            "pencil" to "铅笔",
            "cat" to "猫",
            "dog" to "狗",
            "bird" to "鸟",
            "fish" to "鱼",
            "mother" to "妈妈",
            "father" to "爸爸",
            "friend" to "朋友",
            "family" to "家庭",
            "water" to "水",
            "milk" to "牛奶",
            "book" to "书",
            "class" to "班级",
            "classroom" to "教室",
            "window" to "窗户",
            "door" to "门",
            "desk" to "书桌",
            "chair" to "椅子",
            "bag" to "书包",
            "name" to "名字",
            "boy" to "男孩",
            "girl" to "女孩",
            "red" to "红色",
            "blue" to "蓝色",
            "green" to "绿色",
            "yellow" to "黄色",
            "happy" to "高兴",
            "sad" to "难过",
            "big" to "大的",
            "small" to "小的",
            "long" to "长的",
            "short" to "短的",
            "spring" to "春天",
            "summer" to "夏天",
            "autumn" to "秋天",
            "winter" to "冬天",
            "morning" to "早上",
            "afternoon" to "下午",
            "evening" to "晚上",
            "hello" to "你好",
            "please" to "请",
            "sorry" to "对不起",
            "yes" to "是的",
            "good morning" to "早上好",
            "good afternoon" to "下午好",
            "good night" to "晚安",
            "thank you" to "谢谢",
            "monday" to "星期一",
            "tuesday" to "星期二",
            "wednesday" to "星期三",
            "thursday" to "星期四",
            "friday" to "星期五",
            "saturday" to "星期六",
            "sunday" to "星期日",
        )
        homophones(
            "there",
            "这几个读音很近，要靠意思区分。there 是那里，their 是他们的，they're 是他们是。",
            "there" to "那里",
            "their" to "他们的",
            "they're" to "他们是",
        )
        homophones(
            "to",
            "这几个读音一样。to 是向某处，too 是也、太，two 是数字二。",
            "to" to "向，给",
            "too" to "也，太",
            "two" to "数字二",
        )
        homophones(
            "see",
            "see 是看见，sea 是大海。读音一样。",
            "see" to "看见",
            "sea" to "大海",
        )
        homophones(
            "hear",
            "hear 是听见，here 是这里。",
            "hear" to "听见",
            "here" to "这里",
        )
        homophones(
            "write",
            "write 是写，right 是右边，也可以是对的。",
            "write" to "写",
            "right" to "右边，对的",
        )
        homophones(
            "buy",
            "buy 是买，by 是在旁边、被。",
            "buy" to "买",
            "by" to "在旁边，被",
        )
        homophones(
            "son",
            "son 是儿子，sun 是太阳。",
            "son" to "儿子",
            "sun" to "太阳",
        )
        homophones(
            "meat",
            "meat 是肉，meet 是见面。",
            "meat" to "肉",
            "meet" to "见面",
        )
        homophones(
            "week",
            "week 是星期，weak 是虚弱。",
            "week" to "星期",
            "weak" to "虚弱",
        )
        homophones(
            "know",
            "know 是知道，no 是不。",
            "know" to "知道",
            "no" to "不",
        )
        homophones(
            "where",
            "where 是哪里，wear 是穿。",
            "where" to "哪里",
            "wear" to "穿",
        )
        homophones(
            "for",
            "for 是为了，four 是数字四。",
            "for" to "为了",
            "four" to "数字四",
        )
        homophones(
            "our",
            "our 是我们的，hour 是小时。",
            "our" to "我们的",
            "hour" to "小时",
        )
        homophones(
            "its",
            "its 是它的，it's 是 it is。",
            "its" to "它的",
            "it's" to "它是",
        )
        homophones(
            "then",
            "then 是然后，than 是比。",
            "then" to "然后",
            "than" to "比",
        )
        homophones(
            "quiet",
            "quiet 是安静，quite 是相当。",
            "quiet" to "安静",
            "quite" to "相当",
        )
    }

    private fun MutableMap<String, En>.simple(vararg words: Pair<String, String>) {
        words.forEach { (word, meaning) -> put(word, En(meaning)) }
    }

    private fun MutableMap<String, En>.homophones(id: String, tip: String, vararg words: Pair<String, String>) {
        val all = words.map { it.first }
        words.forEach { (word, meaning) ->
            val others = words.filter { it.first != word }.joinToString("，") { "${it.second}，写成 ${it.first}" }
            put(
                word,
                En(
                    meaning = meaning,
                    avoid = "不要和这些搞混：$others。",
                    family = id,
                    groupTip = tip,
                    companions = all.filter { it != word },
                ),
            )
        }
    }
}
