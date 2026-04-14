import os

input_file = "base.dict.yaml"
high_file = "base_high.dict.yaml"  # 权重 >= 1000 的高频词
low_file = "base_low.dict.yaml"    # 权重 < 1000 的低频词

def process_base():
    if not os.path.exists(input_file):
        print(f"错误：找不到文件 {input_file}")
        return

    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    header = []
    high_data = []
    low_data = []
    is_data = False

    for line in lines:
        raw_line = line.strip()
        # 处理头部信息
        if not is_data:
            header.append(line)
            if raw_line.startswith('...'):
                is_data = True
            continue

        # 处理词条数据
        if '\t' in raw_line:
            parts = raw_line.split('\t')
            try:
                # 尝试获取权重列（通常是第三列）
                weight = int(parts[2]) if len(parts) > 2 else 0
                if weight >= 1000:
                    high_data.append(line)
                else:
                    low_data.append(line)
            except (ValueError, IndexError):
                # 如果权重不是数字或不存在，默认归为低频
                low_data.append(line)

    # 写入高频词库
    with open(high_file, 'w', encoding='utf-8') as f:
        # 修改头部的 name 字段以匹配文件名
        for line in header:
            if line.startswith('name:'):
                f.write("name: base_high\n")
            else:
                f.write(line)
        f.writelines(high_data)

    # 写入低频词库
    with open(low_file, 'w', encoding='utf-8') as f:
        for line in header:
            if line.startswith('name:'):
                f.write("name: base_low\n")
            else:
                f.write(line)
        f.writelines(low_data)

    print(f"处理完成！")
    print(f"高频词 (>=1000): {len(high_data)} 条 -> {high_file}")
    print(f"低频词 (<1000): {len(low_data)} 条 -> {low_file}")

if __name__ == "__main__":
    process_base()