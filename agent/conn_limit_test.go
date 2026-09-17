package main

import (
	"net"
	"testing"
	"time"
)

func TestLimitedListenerBlocksAcceptUntilSlotReleased(t *testing.T) {
	inner, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	listener := newLimitedListener(inner, 1)
	defer listener.Close()

	client1, err := net.Dial("tcp", inner.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer client1.Close()
	server1, err := listener.Accept()
	if err != nil {
		t.Fatal(err)
	}

	client2, err := net.Dial("tcp", inner.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer client2.Close()
	accepted := make(chan net.Conn, 1)
	go func() {
		conn, _ := listener.Accept()
		accepted <- conn
	}()
	select {
	case <-accepted:
		t.Fatal("accepted a connection beyond the active limit")
	case <-time.After(50 * time.Millisecond):
	}
	if err := server1.Close(); err != nil {
		t.Fatal(err)
	}
	select {
	case server2 := <-accepted:
		if server2 == nil {
			t.Fatal("second accept failed")
		}
		_ = server2.Close()
	case <-time.After(time.Second):
		t.Fatal("slot was not released after connection close")
	}
}

func TestLimitedConnectionCloseReleasesSlotOnlyOnce(t *testing.T) {
	releases := 0
	left, right := net.Pipe()
	defer right.Close()
	conn := &limitedConn{Conn: left, release: func() { releases++ }}
	_ = conn.Close()
	_ = conn.Close()
	if releases != 1 {
		t.Fatalf("release count=%d, want 1", releases)
	}
}
