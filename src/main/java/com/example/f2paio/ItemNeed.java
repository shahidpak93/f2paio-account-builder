package com.example.f2paio;

public final class ItemNeed
{
    public final int id;
    public final String name;
    public final int quantity;
    public ItemNeed(int id, String name, int quantity) { this.id = id; this.name = name; this.quantity = quantity; }
    @Override public String toString() { return quantity + " x " + name; }
}
