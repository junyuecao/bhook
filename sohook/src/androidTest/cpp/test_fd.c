// Copyright (c) 2024 SoHook File Descriptor Leak Detection
// Test file descriptor operations implementation

#include "test_fd.h"

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int test_open_file(const char* path, int flags) {
  if (path == NULL) {
    return -1;
  }
  return open(path, flags, 0644);
}

int test_close_file(int fd) {
  if (fd < 0) {
    return -1;
  }
  return close(fd);
}

void* test_fopen_file(const char* path, const char* mode) {
  if (path == NULL || mode == NULL) {
    return NULL;
  }
  return (void*)fopen(path, mode);
}

int test_fclose_file(void* fp) {
  if (fp == NULL) {
    return -1;
  }
  return fclose((FILE*)fp);
}

int test_open_multiple(const char* path_prefix, int count, int* fds) {
  if (path_prefix == NULL || fds == NULL || count <= 0) {
    return 0;
  }

  int opened = 0;
  char path[256];
  
  for (int i = 0; i < count; i++) {
    snprintf(path, sizeof(path), "%s_%d.tmp", path_prefix, i);
    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd >= 0) {
      fds[opened++] = fd;
      // 写入一些数据
      const char* data = "test data\n";
      write(fd, data, strlen(data));
    }
  }
  
  return opened;
}

int test_close_multiple(int* fds, int count) {
  if (fds == NULL || count <= 0) {
    return 0;
  }

  int closed = 0;
  for (int i = 0; i < count; i++) {
    if (fds[i] >= 0 && close(fds[i]) == 0) {
      closed++;
    }
  }
  
  return closed;
}

int test_leak_fd(const char* path) {
  if (path == NULL) {
    return -1;
  }
  
  // 故意不关闭，造成泄漏
  int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
  if (fd >= 0) {
    const char* data = "leaked fd\n";
    write(fd, data, strlen(data));
  }
  return fd;
}

void* test_leak_file(const char* path) {
  if (path == NULL) {
    return NULL;
  }
  
  // 故意不关闭，造成泄漏
  FILE* fp = fopen(path, "w");
  if (fp != NULL) {
    fprintf(fp, "leaked file\n");
    fflush(fp);
  }
  return (void*)fp;
}

int test_write_and_close(const char* path, const char* data, int size) {
  if (path == NULL || data == NULL || size <= 0) {
    return -1;
  }

  int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
  if (fd < 0) {
    return -1;
  }

  int written = write(fd, data, size);
  close(fd);
  
  return (written == size) ? 0 : -1;
}

int test_read_and_close(const char* path, char* buffer, int size) {
  if (path == NULL || buffer == NULL || size <= 0) {
    return -1;
  }

  int fd = open(path, O_RDONLY);
  if (fd < 0) {
    return -1;
  }

  int bytes_read = read(fd, buffer, size - 1);
  if (bytes_read > 0) {
    buffer[bytes_read] = '\0';
  }
  
  close(fd);
  
  return bytes_read;
}
