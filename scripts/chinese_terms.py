"""Shared Chinese UI terminology; English, resource IDs and command syntax stay stable."""
from pathlib import Path
import json
import re

TERMS = json.loads(Path(__file__).with_name('chinese-terms.json').read_text(encoding='utf-8'))

def _pattern(term):
    escaped = re.escape(term)
    if term[0].isascii() and term[0].isalpha():
        escaped = r'(?<![A-Za-z0-9_])' + escaped
    if term[-1].isascii() and term[-1].isalpha():
        escaped += r'(?![A-Za-z0-9_])'
    return escaped

PATTERN = re.compile('|'.join(_pattern(term) for term in sorted(TERMS, key=len, reverse=True)))

def translate(value):
    return PATTERN.sub(lambda match: TERMS[match.group()], value)

def localize(strings):
    result = {key: translate(value) for key, value in strings.items()}
    for key in result:
        if 'melee' in key:
            result[key] = result[key].replace('释放间隔', '伤害间隔')
        result[key] = result[key].replace('持续游戏刻', '持续更新').replace('继续游戏刻', '继续运行')
        result[key] = result[key].replace('高每秒伤害', '高持续伤害')
    result.update({
        'dashboard.sablelife.startDistance': '初始释放位置（格）',
        'dashboard.sablelife.moveSetEditor': '自定义动作抽选列表',
        'dashboard.sablelife.choice_move_set_editor': '编辑自定义动作抽选',
        'manual.sablelife.chapter.marking_system': '标记系统',
        'manual.sablelife.chapter.preview_system': '预览与标记状态',
        'dashboard.sablelife.phase_channel_internal_note': '序列控制／内部',
        'dashboard.sablelife.phase_channel_external_note': '序列控制／外部发射',
        'dashboard.sablelife.targetMultiple': '单目标／多目标',
        'dashboard.sablelife.start_distance_help': '沿发射方向将初始释放位置移到标记前方，数值表示偏移的格数，可避免较大的碰撞箱卡在出口。0保持原位置。激光沿光束方向偏移；地震沿水平朝向偏移，目标脚下模式仍以目标位置为中心。',
        'manual.sablelife.health_phase.settings': '生命值上下限以百分比定义阶段。“持续输出”在区间内保持信号；“进入时脉冲”仅在进入区间时发出脉冲。频道与强度决定信号输出。',
        
    })
    # Polish wording where an English control name previously adjoined Chinese prose.
    replacements = {
        '全身战利品的返回返回模块。': '在全身战利品页面点击“返回”，可回到模块界面。',
        '消失效果在死亡后消失延迟后播放。': '消失效果在死亡延迟结束后播放。',
        '预览左侧还原默认设置仅还原': '预览左侧的“还原默认设置”按钮仅还原',
        '持续200 每秒伤害示例趋近50%减伤；1000 每秒伤害受80%上限限制。': '持续每秒200伤害时，示例趋近50%减伤；每秒1000伤害时，受80%上限限制。',
        '生命值上下百分比': '生命值上下限百分比',
        '持续激活激活': '“持续激活”触发方式激活',
        '释放间隔为两次释放间隔': '释放间隔指两次发射之间的时间',
        '运动轨迹补全补齐': '“运动轨迹补全”用于补齐',
        '仅移动时发出控制发出时机': '“仅移动时发出”决定是否只在移动时生成粒子',
        '在粒子选粒子/大小': '在粒子选择页设置种类与大小',
        '在状态效果加缓慢': '在状态效果选择页添加缓慢',
        '自定义动作抽选动作': '自定义动作抽选中的动作',
        '控制自定义动作抽选': '自定义动作抽选控制器',
        '发射释放间隔': '释放间隔',
        '最大生命滚轮每格5 生命值': '最大生命值滚轮每格调整5点',
    }
    for key, value in result.items():
        for old, new in replacements.items():
            value = value.replace(old, new)
        result[key] = value
    polished=Path(__file__).with_name("chinese-polish.json")
    if polished.exists():result.update(json.loads(polished.read_text(encoding="utf-8")))
    return result
