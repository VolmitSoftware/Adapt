/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.util;

import com.volmit.adapt.Adapt;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

public class TaskExecutor {
    private final ExecutorService service;
    private int xc;

    public TaskExecutor(int threadLimit, int priority, String name) {
        xc = 1;

        if (threadLimit == 1) {
            service = Executors.newSingleThreadExecutor((r) ->
            {
                Thread t = new Thread(r);
                t.setName(name);
                t.setPriority(priority);

                return t;
            });
        } else if (threadLimit > 1) {
            final ForkJoinWorkerThreadFactory factory = pool -> {
                final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName(name + " " + xc++);
                worker.setPriority(priority);
                return worker;
            };

            service = new ForkJoinPool(threadLimit, factory, null, false);
        } else {
            service = Executors.newCachedThreadPool((r) ->
            {
                Thread t = new Thread(r);
                t.setName(name + " " + xc++);
                t.setPriority(priority);

                return t;
            });
        }
    }

    public TaskGroup startWork() {
        return new TaskGroup(this);
    }

    public void close() {
        J.a(() ->
        {
            J.sleep(10000);
            service.shutdown();
        });
    }

    public void closeNow() {
        service.shutdown();
    }

    public enum TaskState {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static class TaskGroup {
        private final List<AssignedTask> tasks;
        private final TaskExecutor e;

        public TaskGroup(TaskExecutor e) {
            tasks = new ArrayList<>();
            this.e = e;
        }

        public TaskGroup queue(NastyRunnable... r) {
            for (NastyRunnable i : r) {
                tasks.add(new AssignedTask(i));
            }

            return this;
        }

        public TaskGroup queue(List<NastyRunnable> r) {
            for (NastyRunnable i : r) {
                tasks.add(new AssignedTask(i));
            }

            return this;
        }

        public TaskResult execute() {
            double timeElapsed = 0;
            int tasksExecuted = 0;
            int tasksFailed = 0;
            int tasksCompleted = 0;
            tasks.forEach((t) -> t.go(e));
            long msv = M.ns();

            waiting:
            while (true) {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e1) {
                    Adapt.verbose("Interrupted while waiting for tasks to complete");
                }

                for (AssignedTask i : tasks) {
                    if (i.state.equals(TaskState.QUEUED) || i.state.equals(TaskState.RUNNING)) {
                        continue waiting;
                    }
                }

                timeElapsed = (double) (M.ns() - msv) / 1000000D;

                for (AssignedTask i : tasks) {
                    if (i.state.equals(TaskState.COMPLETED)) {
                        tasksCompleted++;
                    } else {
                        tasksFailed++;
                    }

                    tasksExecuted++;
                }

                break;
            }

            return new TaskResult(timeElapsed, tasksExecuted, tasksFailed, tasksCompleted);
        }
    }

    @ToString
        public record TaskResult(double timeElapsed, int tasksExecuted, int tasksFailed, int tasksCompleted) {
    }

    @Getter
    public static class AssignedTask {
        private final NastyRunnable task;
        @Setter
        private TaskState state;

        public AssignedTask(NastyRunnable task) {
            this.task = task;
            state = TaskState.QUEUED;
        }

        public void go(TaskExecutor e) {
            e.service.execute(() ->
            {
                state = TaskState.RUNNING;
                try {
                    task.run();
                    state = TaskState.COMPLETED;
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    state = TaskState.FAILED;
                }
            });
        }
    }
}
