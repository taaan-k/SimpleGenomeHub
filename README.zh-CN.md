# SimpleGenomeHub

[English](README.md)

SimpleGenomeHub 是一款基于 Java 11 的跨平台桌面应用，用于基因组数据的本地化管理与分析。它将基因组组装、注释文件、表达矩阵、基因集、区域集、功能注释、BLAST 结果、共线性结果和 Circos 相关文件组织在稳定的本地数据库结构中。

软件的核心数据模型是：一套基因组组装对应一套注释信息。数据导入后，SimpleGenomeHub 会按规范目录自动归档文件，让同一份数据可以被不同分析模块复用，并保证分析结果可以长期保存和追溯。

## 功能亮点

- 本地基因组数据库：支持导入、校验、编辑、导出、删除和目录树浏览
- 基因集与区域集管理：保存分析过程中关注的基因 ID 或基因组区间
- 按基因 ID、转录本 ID、基因组区间提取 FASTA，也支持 CDS、蛋白和启动子/上游序列导出
- 基因 ID 查询、已知 ID 反向追溯物种/基因组来源，并可跳转 TBtools-II 基因结构可视化
- 集成 BLAST 分析流程，支持表格化结果查看、复制、排序、去重和结果转基因集
- 表达矩阵导入、多基因表达探索，并可调用 TBtools-II Heatmap 可视化
- 功能注释导入或自动注释，支持 GO/KEGG 查询和富集分析
- 双基因组共线性、多基因组共线性布局工作区，以及一键 Circos 数据准备
- 通过 GitHub Actions 自动构建 Windows 和 macOS 单文件安装包

## Quick Start

### 1. 安装

从 Releases 下载最新版本：

https://github.com/taaan-k/SimpleGenomeHub/releases

根据平台选择安装包或压缩包：

- Windows：运行 `SimpleGenomeHub-windows-x64-setup.exe`
- macOS：运行 `SimpleGenomeHub-macos-x64.pkg`
- 如果下载的是免安装压缩包，解压后通过 `run.bat`、`run.ps1` 或 `run.sh` 启动

对于较早的 Windows 免安装包，建议将软件放在全英文路径下，并尽量避免路径中包含空格。

### 2. 启动

- Windows 安装包：在开始菜单中打开 `SimpleGenomeHub`
- Windows 免安装包：双击 `run.bat`
- macOS 安装包：在 Applications 中打开 `SimpleGenomeHub.app`
- macOS 免安装包：运行 `run.command` 或 `bash run.sh`

### 3. 配置数据根目录

首次启动后：

1. 点击 `Config`。
2. 选择本地数据库目录。真实基因组项目建议预留至少 20 GB 空间。
3. 点击 `OK`。

该目录将作为后续导入基因组和保存分析结果的主数据库路径。

### 4. 导入基因组

1. 点击 `Import Genome`。
2. 拖入待导入的基因组 FASTA 文件和注释文件。
3. 填写物种、基因组版本、备注等元信息。
4. 点击 `Validate...` 检查基因组文件和注释文件是否匹配。注释文件可能会通过 TBtools-II 的 GXF FIX 流程自动修正。
5. 校验通过后，点击 `Import Species` 完成导入。

导入完成后，新基因组会出现在数据库树中，并可作为当前数据单元参与后续分析。

### 5. 尝试第一个分析

每个新导入的基因组都会自动生成一个包含随机转录本 ID 的 demo gene set。选择该基因组后，可以尝试：

- `Sequence Tools` -> `Fasta Extract`：按基因 ID 或基因集提取转录本、CDS 或蛋白序列
- `Gene Info` -> `Search Gene by ID`：查看某个基因的序列、注释和表达信息
- `BLASTAnalysis`：粘贴查询序列，运行 BLAST，整理结果并从命中结果创建基因集
- `Function Annotation` -> `GO Enrichment Analysis`：对基因列表或已保存基因集进行富集分析

完整图文流程请参考 [示例操作.pdf](示例操作.pdf)。

## 核心流程

### 数据管理

SimpleGenomeHub 将所有基因组相关数据保存在稳定的根目录下。主界面包含三个主要区域：

- 数据库管理：全局配置、导入、导出、删除、基础信息编辑和目录树浏览
- 数据单元信息：物种信息、基因组统计、染色体统计、基因/区域集和其他生成数据
- 功能模块：序列工具、基因信息、BLAST、表达数据、功能注释、基因组分析和可视化相关操作

数据单元可以一键打包导出，便于迁移和备份，避免手工查找分散文件。

### 基因集与区域集

基因集和区域集用于保存分析过程中关注的 ID 或基因组区间，并可作为许多下游功能的输入。

- 基因集以转录本 ID 保存。
- 如果输入的是基因 ID，软件可以自动加入该基因下的所有转录本。
- 区域集保存基因组坐标，便于区域序列提取和可视化。
- 可重新生成随机 demo gene set，用于快速测试功能。

### 序列操作

序列工具支持常用提取任务：

- 按基因或转录本 ID 提取 FASTA
- 直接从已保存基因集加载 ID
- 在转录本、CDS 和蛋白序列之间切换结果
- 按手动输入区间或区域集提取目标基因组区间序列
- 根据所选注释导出 CDS、肽序列和启动子/上游序列

### 基因查询与反向追溯

基因 ID 查询需要先选择一个基因组数据单元，避免不同基因组版本使用相似 ID 命名造成歧义。查询结果可包括：

- 序列
- 功能注释
- 表达记录
- 通过 TBtools-II 进行基因结构可视化

`Identify Species from IDs` 可以在已导入数据中反向搜索已知 ID 的来源，并给出可能的物种/基因组和匹配分数。

## 高级功能

### BLAST 分析

BLAST 界面支持在基因组、基因、转录本和蛋白数据库中进行内部或外部序列相似性搜索。SimpleGenomeHub 会根据查询序列和目标数据库推断 BLAST 类型，调用内置工具运行分析，并在表格中显示结果。结果表支持排序、复制、去重和从命中结果创建基因集。

基因集右键菜单和基因查询结果中也提供 BLAST 快速入口。

### 表达数据

表达矩阵可导入到选定基因组数据单元中。导入后，`Explore Expression Data` 支持通过手动输入 ID 或选择基因集进行多基因表达探索。结果以热图风格表格展示，并可调用 TBtools-II Heatmap 进行可视化。

### 功能注释与富集分析

功能注释既可以手动导入，也可以通过内置的 Java 版 eggNOG-mapper 流程自动生成。用户选择 Tax scope、Backend type 等参数后运行自动注释。注释完成后，可进行交互式注释查询，并用于 GO/KEGG 富集分析。

富集结果可以在确认可视化参数后调用 TBtools-II 进行绘图。

### 双基因组共线性

双基因组共线性分析通过 `MCscanX (Pure Java)` 完成。用户选择两个基因组，确认染色体顺序，可选输入需要高亮的基因，然后运行分析。结果会保存到当前数据单元下，并可在主界面中通过 TBtools-II Dual Synteny Plot 重新打开。

### 多基因组共线性布局

多基因组共线性工作区允许用户先设计布局，再运行分析。支持：

- 拖拽放置基因组
- 网格吸附
- 编辑显示染色体和染色体顺序
- 手动输入或从基因集加载高亮基因
- 在基因组之间创建 Link
- 在显示染色体兼容时复用已有双基因组共线性结果
- 调整 Link 弯曲高度，切换 C 型、S 型或自动路由
- 旋转基因组轨道
- 对差异较大的基因组使用等长展示

最终结果会保存在当前数据单元的 `MultipleCompare` 目录下，并可用 Multiple Synteny Viewer 重新打开。

### Circos 绘图准备

Circos 流程会自动准备基因组信息、轨道、共线性 Link、高亮基因、GC track 和基因密度 track。输出与 TBtools-II Advance Circos 兼容，并会保存到当前数据单元下以便复用。

## 从源码构建

### 环境要求

- Java 11 JDK
- Apache Ant
- Windows 安装包构建需要 Inno Setup，即 `iscc`
- macOS 安装包构建使用 macOS 自带的 `pkgbuild`

仓库不提交内置 Java 运行时。如需构建自包含发布包，请下载 JDK 11 并解压到：

```text
toolchain/windows-x64/
toolchain/macos-x64/
toolchain/linux-x64/
```

需要满足以下路径：

```text
toolchain/windows-x64/bin/java.exe
toolchain/windows-x64/bin/javac.exe
toolchain/macos-x64/bin/java
toolchain/macos-x64/bin/javac
toolchain/linux-x64/bin/java
toolchain/linux-x64/bin/javac
```

### 构建命令

编译应用 jar：

```powershell
ant
```

构建免安装发布包：

```powershell
ant clean release-bundles
```

构建单平台免安装包：

```powershell
ant clean package-windows
ant clean package-macos
```

构建单文件安装包：

```powershell
ant clean installer-windows
ant clean installer-macos
```

输出目录：

- 免安装包：`packages/`
- 安装包：`installers/`

## 仓库结构

```text
src/                       Java 源码
lib/                       Java 依赖，包括 TBtools_JRE1.6.jar
bin/tools/windows-x64/     内置 Windows 命令行工具
bin/tools/macos-x64/       内置 macOS 命令行工具
licenses/                  第三方许可证
scripts/                   平台辅助脚本
toolchain/                 打包用本地 JDK 占位目录
build.xml                  Ant 编译、打包和安装器目标
README.md                  英文文档
README.zh-CN.md            中文文档
```

## 文档

- [Example Workflow.pdf](Example%20Workflow.pdf)：英文图文工作流说明
- [示例操作.pdf](示例操作.pdf)：中文图文工作流说明
