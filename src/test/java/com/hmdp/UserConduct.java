package com.hmdp;

import com.alibaba.fastjson2.support.csv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

@RunWith(SpringRunner.class)
@SpringBootTest
public class UserConduct {
    @Test
    public void test(){
        // 写入位置
        String classpath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        String fileName = classpath+"test/demo.csv";
        // 标题行
        String[] titleRow = {"用户ID", "用户名", "性别"};
        // 数据行
        ArrayList<String[]> dataRows = new ArrayList<>();
        String[] dataRow1 = {"1", "张三", "男"};
        String[] dataRow2 = {"2", "李四", "男"};
        String[] dataRow3 = {"3", "翠花", "女"};
        dataRows.add(dataRow1);
        dataRows.add(dataRow2);
        dataRows.add(dataRow3);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(fileName), Charset.forName("UTF-8"));
            // 1. 通过new CSVWriter对象的方式直接创建CSVWriter对象
            // CSVWriter csvWriter = new CSVWriter(writer);
            // 2. 通过CSVWriterBuilder构造器构建CSVWriter对象
            CSVWriter csvWriter = (CSVWriter) new CSVWriterBuilder(writer)
                    .build();
            // 写入标题行
            csvWriter.writeLine(titleRow, false);
            csvWriter.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
