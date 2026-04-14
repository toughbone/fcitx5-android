import os

input_file = "8105.dict.yaml"
common_file = "common_3500.dict.yaml"
rare_file = "rare_chars.dict.yaml"

with open(input_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

data = []
is_data = False
for line in lines:
    line = line.strip()
    if line.startswith('...'):
        is_data = True
        continue
    if is_data and '\t' in line:
        parts = line.split('\t')
        char = parts[0]
        pinyin = parts[1]
        try:
            # 提取权重数字，如果没有则设为 0
            weight = int(''.join(filter(str.isdigit, parts[2]))) if len(parts) > 2 else 0
        except:
            weight = 0
        data.append((char, pinyin, weight))

# 按照权重降序排列
data.sort(key=lambda x: x[2], reverse=True)

# 拆分数据
common_data = data[:3500]
rare_data = data[3500:]

def save_dict(filename, dict_name, entries):
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(f"---\nname: {dict_name}\nversion: \"1.0\"\nsort: by_weight\n...\n")
        for char, py, weight in entries:
            f.write(f"{char}\t{py}\t{weight}\n")

save_dict(common_file, "common_3500", common_data)
save_dict(rare_file, "rare_chars", rare_data)

print(f"成功拆分！常用字：{len(common_data)}，生僻字：{len(rare_data)}")