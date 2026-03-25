package com.netmgmt.server.monitor;

import com.netmgmt.server.config.Props;
import com.netmgmt.server.model.Device;
import com.netmgmt.server.ssh.LocalCommandExecutor;
import com.netmgmt.server.ssh.SshCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

public final class SshMetricCollector {
  private static final Logger log = LoggerFactory.getLogger(SshMetricCollector.class);

  private static final String METRIC_PS_SCRIPT =
      "$cpu=(Get-CimInstance Win32_Processor | Measure-Object -Property "
          + "LoadPercentage -Average).Average; $os=Get-CimInstance Win32_OperatingSystem; "
          + "if($null -ne $cpu){ Write-Output ('CPU=' + [math]::Round([double]$cpu,2)) }; "
          + "if($null -ne $os -and $os.TotalVisibleMemorySize -gt 0){ "
          + "$mem=[math]::Round((($os.TotalVisibleMemorySize-$os.FreePhysicalMemory)*100.0)"
          + "/$os.TotalVisibleMemorySize,2); Write-Output ('MEM=' + $mem) }";

  private static final String SSH_METRIC_COMMAND =
      "powershell -NoProfile -Command \"" + METRIC_PS_SCRIPT + "\"";

  private static final Set<String> LOCAL_ADDRESSES = Set.of("127.0.0.1", "localhost", "::1");

  private final boolean enabled;
  private final SshCommandExecutor sshExecutor;
  private final LocalCommandExecutor localExecutor;

  public SshMetricCollector(Props props) {
    this.enabled = Boolean.parseBoolean(props.getString("monitor.sshMetricFallbackEnabled", "true"));
    this.sshExecutor = new SshCommandExecutor(props);
    this.localExecutor = new LocalCommandExecutor(props);
  }

  public MetricSample fillMissing(Device device, Double currentCpu, Double currentMem) {
    if (!enabled || (currentCpu != null && currentMem != null)) {
      return new MetricSample(currentCpu, currentMem, false, null);
    }
    if (device == null) {
      return new MetricSample(currentCpu, currentMem, false, null);
    }

    boolean isLocal = isLocalAddress(device.ip());
    boolean hasSshCredentials = device.sshUser() != null && !device.sshUser().isBlank()
        && device.sshPwd() != null && !device.sshPwd().isBlank();

    if (hasSshCredentials && !isLocal) {
      try {
        return parseMetricOutput(sshExecutor.execute(device, SSH_METRIC_COMMAND), currentCpu, currentMem, "SSH");
      } catch (Exception e) {
        log.debug("SSH 补采失败，尝试本机执行: {}", e.getMessage());
      }
    }

    if (isLocal || !hasSshCredentials) {
      try {
        return parseMetricOutput(localExecutor.execute(SSH_METRIC_COMMAND), currentCpu, currentMem, "本机");
      } catch (Exception e) {
        return new MetricSample(currentCpu, currentMem, false, "本机补采失败: " + e.getMessage());
      }
    }

    return new MetricSample(currentCpu, currentMem, false, "无法补采 CPU/内存");
  }

  private MetricSample parseMetricOutput(String output, Double currentCpu, Double currentMem, String source) {
    if (output == null || output.isBlank()) {
      return new MetricSample(currentCpu, currentMem, false, source + " 返回为空");
    }

    Double cpu = currentCpu;
    Double mem = currentMem;
    for (String line : output.split("\\R")) {
      String trimmed = line == null ? "" : line.trim();
      if (trimmed.regionMatches(true, 0, "CPU=", 0, 4)) {
        cpu = cpu != null ? cpu : parsePercent(trimmed.substring(4));
      } else if (trimmed.regionMatches(true, 0, "MEM=", 0, 4)) {
        mem = mem != null ? mem : parsePercent(trimmed.substring(4));
      }
    }

    boolean filled = !Objects.equals(currentCpu, cpu) || !Objects.equals(currentMem, mem);
    if (cpu != null || mem != null) {
      return new MetricSample(cpu, mem, filled, filled ? "已通过" + source + "补采到 Windows CPU/内存" : null);
    }
    return new MetricSample(currentCpu, currentMem, false, source + " 已执行，但未解析到 CPU/内存结果");
  }

  private static boolean isLocalAddress(String ip) {
    if (ip == null) return false;
    return LOCAL_ADDRESSES.contains(ip.trim().toLowerCase());
  }

  private static Double parsePercent(String text) {
    if (text == null) return null;
    String cleaned = text.trim();
    if (cleaned.isEmpty()) return null;
    try {
      double value = Double.parseDouble(cleaned);
      double normalized = Math.max(0.0, Math.min(100.0, value));
      return Math.round(normalized * 100.0) / 100.0;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public record MetricSample(
      Double cpu,
      Double mem,
      boolean filled,
      String message
  ) {}
}
