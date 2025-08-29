package com.hoprxi.rest;


import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.MediaTypeNames;
import com.linecorp.armeria.common.multipart.BodyPart;
import com.linecorp.armeria.common.multipart.Multipart;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.server.annotation.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/20
 */
public class UploadFileService {
    private static final Logger logger = LoggerFactory.getLogger(UploadFileService.class);
    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("user.dir"), "uploads");

    @Post("/upload")
    @Consumes("multipart/form-data")
    @Produces(MediaTypeNames.JSON_UTF_8)
    public CompletableFuture<HttpResponse> upload(Multipart multipart, @Param("rename") @Default("false") boolean rename) {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        // 1. 正确获取StreamMessage并订阅
        StreamMessage<BodyPart> bodyParts = multipart.bodyParts();
        // 2. 创建流处理器
        bodyParts.subscribe(new Subscriber<>() {
                                private Subscription subscription;
                                private final AtomicReference<AsynchronousFileChannel> fileChannelRef = new AtomicReference<>();
                                private final AtomicReference<String> filenameRef = new AtomicReference<>();
                                private final AtomicBoolean fileProcessed = new AtomicBoolean(false);

                                @Override
                                public void onSubscribe(Subscription subscription) {
                                    //this.subscription = subscription;
                                    //subscription.request(1);  // 请求第一个body part
                                }

                                @Override
                                public void onNext(BodyPart part) {
                                    // 只处理文件部分
                                    if ("file".equals(part.name())) {
                                        String fileName = part.filename();
                                        //StringJoiner relativePath = new StringJoiner("/", request.getScheme() + "://" + request.getServerName(), "").add("/images");
                                        StringJoiner relativePath = new StringJoiner("/");
                                        String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                        relativePath.add(folder);
                                        if (rename) {
                                            String extension = fileName.lastIndexOf(".") == -1 ? "" : fileName.substring(fileName.lastIndexOf("."));
                                            String randomName = UUID.randomUUID() + extension;
                                            relativePath.add(randomName);
                                            //path.add(randomName);
                                        } else {
                                            relativePath.add(fileName);
                                            //path.add(fileName);
                                        }

                                        if (fileName != null && !fileName.isEmpty()) {
                                            try {
                                                // 创建文件
                                                Path dest = UPLOAD_DIR.resolve(fileName);
                                                AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                                                        dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                                                fileChannelRef.set(fileChannel);
                                                filenameRef.set(fileName);

                                                // 零拷贝写入
                                                part.content().writeTo((Path) fileChannel, StandardOpenOption.WRITE).thenRun(() -> {
                                                    try {
                                                        fileChannel.close();
                                                        logger.info("Zero-copy upload complete: {}", fileName);
                                                        fileProcessed.set(true);
                                                        responseFuture.complete(HttpResponse.of(
                                                                HttpStatus.OK,
                                                                MediaType.PLAIN_TEXT_UTF_8,
                                                                "Upload successful: " + fileName
                                                        ));
                                                    } catch (Exception e) {
                                                        //handleError(e);
                                                    }
                                                });
                                            } catch (Exception e) {
                                                handleError(e);
                                            }
                                            return;  // 处理完文件后直接返回
                                        }
                                    }

                                }


                                @Override
                                public void onError(Throwable e) {
                                    handleError(e);
                                }

                                @Override
                                public void onComplete() {
                                    if (!fileProcessed.get()) {
                                        responseFuture.complete(HttpResponse.of(
                                                "No file part found in upload",
                                                HttpStatus.BAD_REQUEST));
                                    }
                                }

                                private void handleError(Throwable t) {
                                    logger.error("Upload failed", t);
                                    try {
                                        AsynchronousFileChannel fileChannel = fileChannelRef.get();
                                        if (fileChannel != null && fileChannel.isOpen()) {
                                            fileChannel.close();
                                        }
                                    } catch (Exception e) {
                                        // 忽略
                                    }
                                    responseFuture.complete(HttpResponse.of(
                                            "Upload failed: " + t.getMessage(),
                                            HttpStatus.INTERNAL_SERVER_ERROR
                                    ));
                                }
                            }
        );
        return responseFuture;
    }
}
