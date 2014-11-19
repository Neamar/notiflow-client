package fr.neamar.notiflow.flowdock;

/**
 * Created by neamar on 11/19/14.
 */
public class FlowHelper {
		protected static int[] colors = new int[] { 0x1f77b4, 0xaec7e8, 0xff7f0e, 0xffbb78, 0x2ca02c, 0x98df8a, 0xd62728, 0xff9896, 0x9467bd, 0xc5b0d5, 0x8c564b, 0xc49c94, 0xe377c2, 0xf7b6d2, 0x7f7f7f, 0xc7c7c7, 0xbcbd22, 0xdbdb8d, 0x17becf, 0x9edae5 };

		protected static int hashString (String str) {
				int hash = 0;
				for(int i = 0; i < str.length(); i += 1) {
						hash = ((int) str.charAt(i)) + ((hash << 5) - hash);
				}
				return hash;
		};

		public static int getColorFromName(String flow) {
			return colors[Math.abs(hashString(flow) % colors.length)];
		}
}
