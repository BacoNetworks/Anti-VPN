package me.egg82.antivpn.api.model.source.models;

import flexjson.JSON;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class TeohModel implements SourceModel {

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    private String message;
    private String ip;
    public ArrayList<HashMap<String, Boolean>> security;

    public TeohModel() {
        this.message = null;
        this.ip = null;
        this.security = null;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @JSON(name = "security")
    public ArrayList<HashMap<String, Boolean>> getSecurity() { return security; }

    @JSON(name = "security")
    public void setSecurity(ArrayList<HashMap<String, Boolean>> securityset) { this.security = securityset; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeohModel)) return false;
        return false;
    }

    public int hashCode() { return Objects.hash(message, ip, security); }

    public String toString() {
        return "TeohModel{" +
                "message='" + message + '\'' +
                ", ip='" + ip + '\'' +
                ", security='" + security + '\'' +
                '}';
    }
}
