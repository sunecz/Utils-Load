package sune.util.load;

@FunctionalInterface
public interface ContentsResolver {
	
	byte[] bytes(String path) throws Exception;
}