

## 关系型DB从0到1——基于Java语言的简易数据库

本项目旨在练习实现一个基于Java语言的简易关系型数据库，用来学习关系型数据库(如Mysql)的设计理念、核心骨架、基本算法。

涉及缓存、数据底层存储结构(B+树)、锁、事务、优化器、redo/undo log等核心原理。

### 1.关系数据结构基本定义

添加数据库、表、行、字段等基础定义



### 2.数据持久化

每一个表存储成为一个物理磁盘文件，随着表数据变多，一个表对应的物理磁盘文件可以无限变大，全部读取到内存中肯定是不可取的，编写程序时应每次磁盘IO读取一个数据块，操作系统与磁盘一般是以4k为以页的单位来交互的，因此我们读写数据也以4KB page为基本单位。

添加文件、页、表结构描述等定义
添加表文件（DbFile）的方法: writePageToDisk、readPageFromDisk，实现从表的磁盘文件中写入一页数据、读取一页数据

完成 单例dataBase对象
完成 page中需要依赖table相关的属性，新增pageID类，以便于在page中引用table
完成 heapPage 序列化、反序列化及page落盘持久化



**小结:实现了以下功能**

1. 创建数据库实例,单例的DataBase对象
2. 新增表,目前字段类型仅支持int类型
3. 插入行数据，仅内核实现，不支持sql解析
4. 数据的落盘持久化
   - 数据目录组织：存取均以page单位
   - 底层存储结构：page中数据的基本存储格式为插槽(slot)状态位+行数据+末尾填充字节



### 3.操作符(operator)

操作符是对表中数据的最底层操作，通常有以下几种：

- 全表扫描：不带where条件的select * 获取表中所有数据

- 条件过滤：条件有>、<、=、<=、>= 、!=、like等等
  
   - 表的连接：多个表的join
   
- 聚合: sum、average等
   - 等等
   
     

   上层将底层的多个操作符进行组合，以实现特定的操作。比如：select * from table_a where id>100; 组合了全表扫描+条件过滤2个操作符。

   
   
   **小结:操作符部分实现了以下功能**
   
   1. 最基本操作符，表中数据顺序扫描 Seq
   2. Filter条件过滤
   3. OrderBy排序
   4. Aggregate聚合，目前只支持int字段，后续实现其他类型
   5. Join 连接
   6. Delete 删除
   
### 4.基于B+Tree的的文件组织与索引

   聚簇索引的定义：如果数据记录(即行记录)的顺序与某个索引的key顺序相同，那么就称这一索引为聚簇索引。

   在用B+Tree组织数据时，聚簇索引树的叶子节点存储的都是行记录，即整张表数据，访问时从树的根节点一级一级向下找，直至找到叶子节点，读取行数据。

   

   #### B+Tree  文件布局

![pic1-页的结构](./doc/img/pic1.png)



页类型区分为四种：RootPTRPage、HeaderPage、InternalPage、LeafPage

RootPTRPage格式如下，记录树的根节点所在的pageNo、根节点所在Page的类型（internal 或leaf）、第一个HeaderPage

![pic2](./doc/img/pic2.png)



HeaderPage格式如下，作用是跟踪整个文件中使用的页，记录的使用状态，用于回收和重用，所有的header page 以双向链表连接

![](./doc/img/pic5.png)



InternalPage 格式如下，用于存储索引值

TODO 已经重构，图片待更新

![](./doc/img/pic4.png)



LeafPage格式如下，聚簇索引中存储整行数据，普通索引中存放索引value本身

![](./doc/img/pic3.png)



#### B+树 表的存储

以下存储一个表的示例，图中的Node对应上面的一个page（如图中的[99,203]标示的是一页中存储两个元素），在micro-DB B+树实现中，每次存储或读取都是以页为单位。

![](./doc/img/pic6.png)



#### 基于B+树的表文件存储基本操作

##### 页分裂

Leaf page 分裂：页分裂发生在插入数据时，当向一个叶子页中插入数据时，当该叶子页存满，会触发页分裂：创建一个新页并把原页中的元素按顺序分布到两个页面中，然后将中间位置的索引值提取到树的上一层，如果该上一层也满，则递归触发页分裂。

internal page 分裂：与leaf page类似，但存在一点区别，如图所示，leaf page提取索引到上层，是"复制"操作，internal page 提取索引到上层，是”移动“操作。

![](./doc/img/pic7.png)

##### 页合并

页合并发生在删除数据时，随着页内元素的删除，会出现许多空洞，页合并的目的是为了保证空间利用率。

当某个页面在删除元素后，元素数量不足页半满，如果它的兄弟页也不足半满，则将两个页合并，释放空间。

如图所示，当合并后，合并页在上层的索引需要删除

![](./doc/img/pic8.png)



##### 页重分布

重分布发生在删除数据时，与页合并的目的相同，一方面提升空间利用率，一方面为了维持树的平衡。

当某个页面在删除元素后，元素数量不足页半满，但兄弟页有足够的元素（超过半满），则从兄弟页中挪用元素补充，以保持半满及以上。

![](./doc/img/pic9.png)



### 5. Buffer Pool 缓冲池（开发中...）

未使用缓存池的实现中，当对页做了修改时立即刷盘、每次读取页时需要从磁盘读入，磁盘IO非常多，是一个性能瓶颈

通过缓存池来解决这个问题，保持常用的页面常驻内存、脏页(修改过的页)不必立即刷新，通过一定的驱逐策略，待缓存池满时，将使用频率低的页面从缓存中删除。











  















