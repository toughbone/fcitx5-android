import os

# 需要修复的文件列表
dict_files = ["toughbone.dict.yaml", "base_high.dict.yaml", "common_3500.dict.yaml"]

def fix_format_v2(filename):
    if not os.path.exists(filename):
        print(f"跳过：找不到文件 {filename}")
        return

    print(f"正在处理：{filename} ...")

    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    fixed_lines = []
    is_data_section = False

    for line in lines:
        if line.strip() == "...":
            is_data_section = True
            fixed_lines.append(line)
            continue

        if not is_data_section or not line.strip() or line.startswith('#'):
            fixed_lines.append(line)
            continue

        # 核心逻辑：
        # 1. 找到第一个空格/Tab，那是汉字和拼音的分界线
        # 2. 找到最后一组数字，那是权重（如果有）

        line_content = line.strip()
        # 匹配第一个汉字部分（非空白字符）
        # 匹配中间拼音部分（包含字母和空格）
        # 匹配末尾数字部分（权重）

        import re
        # 正则：(汉字)\s+(拼音及拼音间空格)\s*(权重数字)?
        match = re.match(r'^([^\s\t]+)\s+(.+?)(?:\s+(\d+))?$', line_content)

        if match:
            char = match.group(1)
            pinyin = match.group(2)
            weight = match.group(3)

            if weight:
                new_line = f"{char}\t{pinyin}\t{weight}\n"
            else:
                new_line = f"{char}\t{pinyin}\n"
            fixed_lines.append(new_line)
        else:
            fixed_lines.append(line)

    with open(filename, 'w', encoding='utf-8') as f:
        f.writelines(fixed_lines)
    print(f"修正完成：{filename}")

if __name__ == "__main__":
    for d_file in dict_files:
        fix_format_v2(d_file)
    print("\n脚本执行完毕！拼音内部空格已保留。")
