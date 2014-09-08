/*
 * Copyright 2012 The Netty Project
 * 
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.stratio.deep.core.extractor.client;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.spark.Partition;

import com.stratio.deep.config.ExtractorConfig;
import com.stratio.deep.extractor.actions.CloseAction;
import com.stratio.deep.extractor.actions.ExtractorInstanceAction;
import com.stratio.deep.extractor.actions.GetPartitionsAction;
import com.stratio.deep.extractor.actions.HasNextAction;
import com.stratio.deep.extractor.actions.InitIteratorAction;
import com.stratio.deep.extractor.actions.InitSaveAction;
import com.stratio.deep.extractor.actions.SaveAction;
import com.stratio.deep.extractor.response.ExtractorInstanceResponse;
import com.stratio.deep.extractor.response.GetPartitionsResponse;
import com.stratio.deep.extractor.response.HasNextElement;
import com.stratio.deep.extractor.response.HasNextResponse;
import com.stratio.deep.extractor.response.Response;
import com.stratio.deep.rdd.IExtractor;

public class ExtractorClientHandler<T> extends SimpleChannelInboundHandler<Response> implements
    IExtractor<T> {

  // Stateful properties
  private volatile Channel channel;

  private ExecutorService executor;

  private final BlockingQueue<Response> answer = new LinkedBlockingQueue<Response>();

  private HasNextElement<T> nextElement = null;

  private LinkedList<HasNextElement<T>> currentPage;

  private Future<LinkedList<HasNextElement<T>>> nextPage;

  public ExtractorClientHandler() {
    super(true);
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) {
    channel = ctx.channel();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * io.netty.channel.SimpleChannelInboundHandler#channelRead0(io.netty.channel.ChannelHandlerContext
   * , java.lang.Object)
   */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Response msg) throws Exception {
    answer.add(msg);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.stratio.deep.rdd.IDeepRDD#getPartitions(org.apache.spark.broadcast.Broadcast, int)
   */
  @Override
  public Partition[] getPartitions(ExtractorConfig<T> config) {

    channel.writeAndFlush(new GetPartitionsAction<>(config));

    Response response;
    boolean interrupted = false;
    for (;;) {
      try {
        response = answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    return ((GetPartitionsResponse) response).getPartitions();
  }

  @Override
  public void close() {

    executor.shutdown();

    channel.writeAndFlush(new CloseAction());

    boolean interrupted = false;
    for (;;) {
      try {
        answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    return;
  }

  @Override
  public boolean hasNext() {

    if (currentPage == null) {
      try {
        this.currentPage = retrieveNextInformation().get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      this.nextPage = retrieveNextInformation();

      this.nextElement = currentPage.poll();
    }

    return nextElement.getHasNext();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Future<LinkedList<HasNextElement<T>>> retrieveNextInformation() {

    Future<LinkedList<HasNextElement<T>>> future = executor.submit(new Callable() {
      public Object call() throws Exception {
        channel.writeAndFlush(new HasNextAction<>());
        return ((HasNextResponse) answer.take()).getHasNextElementsPage();
      }
    });

    return future;
  }

  @Override
  public T next() {

    T currentValue = nextElement.getData();

    if (currentPage.isEmpty()) {
      try {
        currentPage = nextPage.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      this.nextPage = retrieveNextInformation();
    }

    nextElement = currentPage.poll();

    return currentValue;
  }

  @Override
  public void initIterator(Partition dp, ExtractorConfig<T> config) {

    this.executor = Executors.newFixedThreadPool(2);

    channel.writeAndFlush(new InitIteratorAction<>(dp, config));

    boolean interrupted = false;
    for (;;) {
      try {
        answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return;
  }

  @Override
  public IExtractor<T> getExtractorInstance(ExtractorConfig<T> config) {

    channel.writeAndFlush(new ExtractorInstanceAction<>(config));

    Response response;
    boolean interrupted = false;
    for (;;) {
      try {
        response = answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    return ((ExtractorInstanceResponse<T>) response).getData();
  }

  @Override
  public void saveRDD(T t) {

    channel.writeAndFlush(new SaveAction<>(t));

    boolean interrupted = false;
    for (;;) {
      try {
        answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    return;
  }

  @Override
  public void initSave(ExtractorConfig<T> config, T first) {

    channel.writeAndFlush(new InitSaveAction<>(config, first));

    boolean interrupted = false;
    for (;;) {
      try {
        answer.take();
        break;
      } catch (InterruptedException ignore) {
        interrupted = true;
      }
    }

    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    return;
  }



}
