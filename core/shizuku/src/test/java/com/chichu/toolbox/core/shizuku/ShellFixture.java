package com.chichu.toolbox.core.shizuku;

public final class ShellFixture {
    public static void main(String[] args) {
        if (args.length > 0 && "large".equals(args[0])) {
            for (int i = 0; i < 1000; i++) System.out.println("abcdefghij");
        } else {
            System.out.println("output");
            System.err.println("error");
            System.exit(7);
        }
    }
}
