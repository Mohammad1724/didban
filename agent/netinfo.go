package main

import (
	"bufio"
	"encoding/hex"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// SocketInfo represents an active network endpoint or connection.
type SocketInfo struct {
	Proto      string `json:"proto"`
	LocalIP    string `json:"local_ip"`
	LocalPort  int    `json:"local_port"`
	RemoteIP   string `json:"remote_ip,omitempty"`
	RemotePort int    `json:"remote_port,omitempty"`
	State      string `json:"state"`
	PID        int    `json:"pid,omitempty"`
	Process    string `json:"process,omitempty"`
	Inode      uint64 `json:"inode"`
}

// SocketsResponse contains categorized listening ports and active sockets.
type SocketsResponse struct {
	Listening   []SocketInfo `json:"listening"`
	Connections []SocketInfo `json:"connections"`
}

var tcpStates = map[string]string{
	"01": "ESTABLISHED",
	"02": "SYN_SENT",
	"03": "SYN_RECV",
	"04": "FIN_WAIT1",
	"05": "FIN_WAIT2",
	"06": "TIME_WAIT",
	"07": "CLOSE",
	"08": "CLOSE_WAIT",
	"09": "LAST_ACK",
	"0A": "LISTEN",
	"0B": "CLOSING",
}

func parseIPv4(hexIP string) string {
	b, err := hex.DecodeString(hexIP)
	if err != nil || len(b) != 4 {
		return hexIP
	}
	return fmt.Sprintf("%d.%d.%d.%d", b[3], b[2], b[1], b[0])
}

func parseIPv6(hexIP string) string {
	b, err := hex.DecodeString(hexIP)
	if err != nil || len(b) != 16 {
		return hexIP
	}
	var ipBytes [16]byte
	for i := 0; i < 4; i++ {
		ipBytes[i*4+0] = b[i*4+3]
		ipBytes[i*4+1] = b[i*4+2]
		ipBytes[i*4+2] = b[i*4+1]
		ipBytes[i*4+3] = b[i*4+0]
	}
	ip := net.IP(ipBytes[:])
	return ip.String()
}

func parseHexPort(hexPort string) int {
	p, _ := strconv.ParseInt(hexPort, 16, 32)
	return int(p)
}

func buildInodeMap() map[uint64]struct{ pid int; name string } {
	res := make(map[uint64]struct{ pid int; name string })
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return res
	}
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		name := ""
		if comm, err := os.ReadFile(filepath.Join("/proc", e.Name(), "comm")); err == nil {
			name = strings.TrimSpace(string(comm))
		}
		fdDir := filepath.Join("/proc", e.Name(), "fd")
		fds, err := os.ReadDir(fdDir)
		if err != nil {
			continue
		}
		for _, fd := range fds {
			link, err := os.Readlink(filepath.Join(fdDir, fd.Name()))
			if err != nil {
				continue
			}
			if strings.HasPrefix(link, "socket:[") && strings.HasSuffix(link, "]") {
				inodeStr := link[8 : len(link)-1]
				if inode, err := strconv.ParseUint(inodeStr, 10, 64); err == nil {
					res[inode] = struct{ pid int; name string }{pid: pid, name: name}
				}
			}
		}
	}
	return res
}

func parseNetFile(path, proto string, isIPv6 bool, inodeMap map[uint64]struct{ pid int; name string }) []SocketInfo {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()
	var out []SocketInfo
	sc := bufio.NewScanner(f)
	lineNum := 0
	for sc.Scan() {
		lineNum++
		if lineNum == 1 {
			continue // header
		}
		fields := strings.Fields(sc.Text())
		if len(fields) < 10 {
			continue
		}
		localParts := strings.Split(fields[1], ":")
		remParts := strings.Split(fields[2], ":")
		if len(localParts) != 2 || len(remParts) != 2 {
			continue
		}
		localIP := parseIPv4(localParts[0])
		remoteIP := parseIPv4(remParts[0])
		if isIPv6 {
			localIP = parseIPv6(localParts[0])
			remoteIP = parseIPv6(remParts[0])
		}
		localPort := parseHexPort(localParts[1])
		remotePort := parseHexPort(remParts[1])
		stHex := strings.ToUpper(fields[3])
		state := tcpStates[stHex]
		if state == "" {
			state = "UDP"
		}
		inode, _ := strconv.ParseUint(fields[9], 10, 64)
		s := SocketInfo{
			Proto:      proto,
			LocalIP:    localIP,
			LocalPort:  localPort,
			RemoteIP:   remoteIP,
			RemotePort: remotePort,
			State:      state,
			Inode:      inode,
		}
		if proc, ok := inodeMap[inode]; ok {
			s.PID = proc.pid
			s.Process = proc.name
		}
		out = append(out, s)
	}
	return out
}

// GetNetworkSockets collects all listening ports and active sockets.
func GetNetworkSockets() SocketsResponse {
	inodeMap := buildInodeMap()
	var all []SocketInfo

	all = append(all, parseNetFile("/proc/net/tcp", "tcp", false, inodeMap)...)
	all = append(all, parseNetFile("/proc/net/tcp6", "tcp6", true, inodeMap)...)
	all = append(all, parseNetFile("/proc/net/udp", "udp", false, inodeMap)...)
	all = append(all, parseNetFile("/proc/net/udp6", "udp6", true, inodeMap)...)

	var listening []SocketInfo
	var connections []SocketInfo

	for _, s := range all {
		if s.State == "LISTEN" || (s.Proto == "udp" && s.RemotePort == 0) || (s.Proto == "udp6" && s.RemotePort == 0) {
			listening = append(listening, s)
		} else {
			connections = append(connections, s)
		}
	}

	sort.Slice(listening, func(i, j int) bool {
		return listening[i].LocalPort < listening[j].LocalPort
	})

	sort.Slice(connections, func(i, j int) bool {
		if connections[i].State != connections[j].State {
			return connections[i].State < connections[j].State
		}
		return connections[i].LocalPort < connections[j].LocalPort
	})

	return SocketsResponse{
		Listening:   listening,
		Connections: connections,
	}
}
