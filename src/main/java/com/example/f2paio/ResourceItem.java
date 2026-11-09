package com.example.f2paio;

public final class ResourceItem
{
    public int id;
    public String name;
    public int quantity;
    public boolean noted;
    public boolean noteable;
    public int baseId;
    public boolean stackable;
    public ResourceItem(int id,String name,int quantity,boolean noted,int baseId,boolean noteable) {
        this(id,name,quantity,noted,baseId,noteable,noted);
    }
    public ResourceItem(int id,String name,int quantity,boolean noted,int baseId,boolean noteable,boolean stackable) {
        this(id,name,quantity); this.noted=noted; this.baseId=baseId; this.noteable=noteable; this.stackable=stackable;
    }
    public int canonicalId() { return baseId>0?baseId:id; }
    public ResourceItem(int id, String name, int quantity)
    {
        this.id = id; this.name = name; this.quantity = quantity; this.baseId=id;
    }
}
